package team.startup.expo.internal

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.domain.training.entity.Trainee
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.support.IntegrationTestSupport
import java.time.LocalDateTime

class InternalParticipantApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var standardParticipantRepository: StandardParticipantRepository

    @Autowired
    private lateinit var traineeRepository: TraineeRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    private var standard1 = 0L
    private var standard2 = 0L
    private var standardOtherExpo = 0L
    private var traineeSharingPhone = 0L
    private var acceptedAdminId = 0L

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_trainee, tb_standard_participant, tb_admin RESTART IDENTITY CASCADE")
        standard1 = saveStandard(EXPO_A, PHONE_1, "홍길동")
        standard2 = saveStandard(EXPO_A, PHONE_2, "김영희")
        standardOtherExpo = saveStandard(EXPO_B, PHONE_3, "다른행사")
        // id가 일반 참가자와 겹치지 않도록 연수자를 하나 먼저 만든다
        saveTrainee(EXPO_A, "01099990000", "T-0000")
        // 같은 박람회에서 일반 참가자 standard1과 같은 번호로 신청한 연수자
        traineeSharingPhone = saveTrainee(EXPO_A, PHONE_1, "T-0001")
        acceptedAdminId =
            adminRepository
                .save(
                    Admin(
                        name = "관리자",
                        nickname = "admin",
                        email = "admin@gsm.hs.kr",
                        password = "encoded",
                        phoneNumber = "01000000000",
                    ).apply {
                        accept()
                    },
                ).id!!
    }

    // --- POST /internal/standard-participants/resolve

    @Test
    fun `박람회와 전화번호로 일반 참가자 id를 찾는다`() {
        resolveStandard(EXPO_A, PHONE_1)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participantId").value(standard1))
            .andExpect(jsonPath("$.name").doesNotExist())
            .andExpect(jsonPath("$.phoneNumber").doesNotExist())
    }

    @Test
    fun `없는 번호나 다른 박람회의 번호는 404이다`() {
        resolveStandard(EXPO_A, "01077778888").andExpect(status().isNotFound)
        resolveStandard(EXPO_B, PHONE_1).andExpect(status().isNotFound)
        resolveStandard("no-such-expo", PHONE_1).andExpect(status().isNotFound)
    }

    @Test
    fun `일반 참가자 조회의 입력이 올바르지 않으면 400이다`() {
        resolveStandard(EXPO_A, "010-1111-2222").andExpect(status().isBadRequest)
        resolveStandard(EXPO_A, "").andExpect(status().isBadRequest)
        resolveStandard(" ", PHONE_1).andExpect(status().isBadRequest)
        postInternal("/internal/standard-participants/resolve", "{}").andExpect(status().isBadRequest)
    }

    // --- POST /internal/participants/resolve

    @Test
    fun `같은 번호가 두 테이블에 있어도 지정한 구분의 참가자를 찾는다`() {
        resolveParticipant(EXPO_A, PHONE_1, "STANDARD")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participantId").value(standard1))
            .andExpect(jsonPath("$.participationType").value("STANDARD"))
        resolveParticipant(EXPO_A, PHONE_1, "TRAINEE")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participantId").value(traineeSharingPhone))
            .andExpect(jsonPath("$.participationType").value("TRAINEE"))
        check(standard1 != traineeSharingPhone) { "두 id가 달라야 구분이 검증된다" }
    }

    @Test
    fun `지정한 구분에 없는 번호는 다른 구분에 있어도 404이다`() {
        resolveParticipant(EXPO_A, PHONE_2, "TRAINEE").andExpect(status().isNotFound)
        resolveParticipant(EXPO_B, PHONE_1, "STANDARD").andExpect(status().isNotFound)
    }

    @Test
    fun `응답자 구분이 없거나 알 수 없는 값이면 400이다`() {
        resolveParticipant(EXPO_A, PHONE_1, "ADMIN").andExpect(status().isBadRequest)
        postInternal("/internal/participants/resolve", """{"expoId":"$EXPO_A","phoneNumber":"$PHONE_1"}""").andExpect(status().isBadRequest)
    }

    // --- POST /internal/standard-participants/names

    @Test
    fun `요청한 id의 이름을 요청 순서대로 돌려준다`() {
        names(EXPO_A, listOf(standard2, standard1))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].participantId").value(standard2))
            .andExpect(jsonPath("$[0].name").value("김영희"))
            .andExpect(jsonPath("$[1].participantId").value(standard1))
            .andExpect(jsonPath("$[1].name").value("홍길동"))
            .andExpect(jsonPath("$[0].phoneNumber").doesNotExist())
    }

    @Test
    fun `중복 id는 한 번만 돌려준다`() {
        names(EXPO_A, listOf(standard1, standard1))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `없는 id나 다른 박람회의 참가자가 하나라도 있으면 일부만 돌려주지 않고 404이다`() {
        names(EXPO_A, listOf(standard1, 99999L)).andExpect(status().isNotFound).andExpect(jsonPath("$[0]").doesNotExist())
        names(EXPO_A, listOf(standard1, standardOtherExpo)).andExpect(status().isNotFound)
        names(EXPO_A, listOf(standardOtherExpo)).andExpect(status().isNotFound)
    }

    @Test
    fun `id 목록이 비었거나 너무 많으면 400이다`() {
        names(EXPO_A, emptyList()).andExpect(status().isBadRequest)
        names(EXPO_A, (1L..10_001L).toList()).andExpect(status().isBadRequest)
        postInternal("/internal/standard-participants/names", """{"expoId":"$EXPO_A"}""").andExpect(status().isBadRequest)
    }

    @Test
    fun `501개 이상도 한 번에 조회하고 요청 순서를 유지한다`() {
        val ids = (1..2_500).map { saveStandard(EXPO_A, "0102%07d".format(it), "참가자$it") }
        val requested = ids.reversed() + standard1

        names(EXPO_A, requested)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(requested.size))
            .andExpect(jsonPath("$[0].participantId").value(ids.last()))
            .andExpect(jsonPath("$[0].name").value("참가자2500"))
            .andExpect(jsonPath("$[1249].participantId").value(ids[2500 - 1250]))
            .andExpect(jsonPath("$[2499].name").value("참가자1"))
            .andExpect(jsonPath("$[2500].participantId").value(standard1))
    }

    @Test
    fun `상한까지는 크기만으로 거절하지 않는다`() {
        // 존재하지 않는 id가 섞여 있으므로 400이 아니라 404여야 한다
        names(EXPO_A, (1L..10_000L).toList()).andExpect(status().isNotFound)
    }

    // --- 인증

    @Test
    fun `토큰이 없으면 모든 내부 경로가 401이다`() {
        INTERNAL_PATHS.forEach { (path, body) ->
            mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized)
        }
    }

    @Test
    fun `토큰이 틀리면 401이다`() {
        INTERNAL_PATHS.forEach { (path, body) ->
            listOf("wrong-token", "", INTERNAL_TOKEN + "x", INTERNAL_TOKEN.dropLast(1)).forEach { token ->
                mockMvc
                    .perform(post(path).header("X-Internal-Token", token).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized)
            }
        }
    }

    @Test
    fun `gateway 헤더만으로는 내부 경로에 접근할 수 없다`() {
        INTERNAL_PATHS.forEach { (path, body) ->
            mockMvc
                .perform(post(path).header("X-User-Id", acceptedAdminId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized)
        }
    }

    @Test
    fun `올바른 토큰이 있으면 X-User-Id는 무시된다`() {
        mockMvc
            .perform(
                post("/internal/standard-participants/resolve")
                    .header("X-Internal-Token", INTERNAL_TOKEN)
                    .header("X-User-Id", 99999)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"expoId":"$EXPO_A","phoneNumber":"$PHONE_1"}"""),
            ).andExpect(status().isOk)
    }

    @Test
    fun `내부 토큰은 내부 경로 밖에서는 인증이 되지 않는다`() {
        mockMvc.perform(get("/admin/my").header("X-Internal-Token", INTERNAL_TOKEN)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `관리자는 내부 경로를 쓸 수 없다`() {
        mockMvc
            .perform(
                get("/internal/standard-participants/resolve").header("X-User-Id", acceptedAdminId),
            ).andExpect(status().isUnauthorized)
    }

    private fun resolveStandard(
        expoId: String,
        phoneNumber: String,
    ) = postInternal("/internal/standard-participants/resolve", """{"expoId":"$expoId","phoneNumber":"$phoneNumber"}""")

    private fun resolveParticipant(
        expoId: String,
        phoneNumber: String,
        participationType: String,
    ) = postInternal(
        "/internal/participants/resolve",
        """{"expoId":"$expoId","phoneNumber":"$phoneNumber","participationType":"$participationType"}""",
    )

    private fun names(
        expoId: String,
        ids: List<Long>,
    ) = postInternal(
        "/internal/standard-participants/names",
        """{"expoId":"$expoId","participantIds":${ids.joinToString(",", "[", "]")}}""",
    )

    private fun postInternal(
        path: String,
        body: String,
    ): ResultActions =
        mockMvc.perform(
            post(path).header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON).content(body),
        )

    private fun saveStandard(
        expoId: String,
        phoneNumber: String,
        name: String,
    ) = standardParticipantRepository
        .save(
            StandardParticipant(
                expoId = expoId,
                name = name,
                phoneNumber = phoneNumber,
                personalInformationStatus = true,
                applicationType = ApplicationType.PRE,
                applicationDate = LocalDateTime.of(2026, 10, 1, 10, 0),
            ),
        ).id!!

    private fun saveTrainee(
        expoId: String,
        phoneNumber: String,
        trainingId: String,
    ) = traineeRepository
        .save(
            Trainee(
                expoId = expoId,
                name = "연수자",
                phoneNumber = phoneNumber,
                trainingId = trainingId,
                personalInformationStatus = true,
                applicationType = ApplicationType.PRE,
                applicationDate = LocalDateTime.of(2026, 10, 1, 10, 0),
            ),
        ).id!!

    private companion object {
        const val EXPO_A = "0199aaaa-0000-7000-8000-00000000000a"
        const val EXPO_B = "0199aaaa-0000-7000-8000-00000000000b"
        const val PHONE_1 = "01011112222"
        const val PHONE_2 = "01033334444"
        const val PHONE_3 = "01055556666"

        val INTERNAL_PATHS =
            listOf(
                "/internal/standard-participants/resolve" to """{"expoId":"$EXPO_A","phoneNumber":"$PHONE_1"}""",
                "/internal/standard-participants/names" to """{"expoId":"$EXPO_A","participantIds":[1]}""",
                "/internal/participants/resolve" to """{"expoId":"$EXPO_A","phoneNumber":"$PHONE_1","participationType":"STANDARD"}""",
            )
    }
}
