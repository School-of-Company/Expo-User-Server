package team.startup.expo.internal

import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
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
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.domain.training.entity.Trainee
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.support.IntegrationTestSupport
import java.time.LocalDateTime

class InternalTraineeApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var traineeRepository: TraineeRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    private var trainee1 = 0L
    private var trainee2 = 0L
    private var traineeOtherExpo = 0L
    private var adminId = 0L

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_trainee, tb_admin RESTART IDENTITY CASCADE")
        trainee1 = save(EXPO_A, "홍길동", "01011112222", "T-0001")
        trainee2 = save(EXPO_A, "김영희", "01033334444", "T-0002")
        traineeOtherExpo = save(EXPO_B, "다른행사", "01055556666", "T-0001")
        adminId =
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

    // --- POST /internal/trainees/resolve

    @Test
    fun `박람회와 연수 번호로 연수자 id를 찾는다`() {
        resolve(EXPO_A, "T-0001")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.traineeId").value(trainee1))
            .andExpect(jsonPath("$.name").doesNotExist())
            .andExpect(jsonPath("$.phoneNumber").doesNotExist())
    }

    @Test
    fun `같은 연수 번호라도 박람회가 다르면 그 박람회의 연수자를 찾는다`() {
        resolve(EXPO_B, "T-0001").andExpect(status().isOk).andExpect(jsonPath("$.traineeId").value(traineeOtherExpo))
    }

    @Test
    fun `없는 연수 번호나 다른 박람회의 번호는 404이다`() {
        resolve(EXPO_A, "T-9999").andExpect(status().isNotFound)
        resolve(EXPO_B, "T-0002").andExpect(status().isNotFound)
        resolve("no-such-expo", "T-0001").andExpect(status().isNotFound)
    }

    @Test
    fun `같은 박람회에 같은 연수 번호가 여럿이면 임의의 한 명을 돌려주지 않고 409이다`() {
        save(EXPO_A, "중복자", "01077778888", "T-0001")

        resolve(EXPO_A, "T-0001").andExpect(status().isConflict).andExpect(jsonPath("$.traineeId").doesNotExist())
        // 중복이 아닌 번호는 영향이 없다
        resolve(EXPO_A, "T-0002").andExpect(status().isOk).andExpect(jsonPath("$.traineeId").value(trainee2))
    }

    @Test
    fun `연수자 조회의 입력이 올바르지 않으면 400이다`() {
        resolve(EXPO_A, "").andExpect(status().isBadRequest)
        resolve(" ", "T-0001").andExpect(status().isBadRequest)
        resolve(EXPO_A, "T".repeat(16)).andExpect(status().isBadRequest)
        postInternal("/internal/trainees/resolve", "{}").andExpect(status().isBadRequest)
    }

    @Test
    fun `연수 번호는 URL이나 쿼리로 받지 않는다`() {
        // 본문 없이 쿼리로만 보내면 연수 번호를 읽지 않는다(2xx가 아니다)
        val result =
            mockMvc
                .perform(
                    post(
                        "/internal/trainees/resolve",
                    ).param("trainingId", "T-0001").param("expoId", EXPO_A).header("X-Internal-Token", INTERNAL_TOKEN),
                ).andReturn()
                .response.status
        check(result in 400..499) { "쿼리로 보낸 연수 번호가 받아들여지면 안 된다: $result" }
    }

    // --- POST /internal/trainees/names

    @Test
    fun `요청한 id의 이름을 요청 순서대로 돌려주고 중복 id는 한 번만 준다`() {
        names(EXPO_A, listOf(trainee2, trainee1, trainee2))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].traineeId").value(trainee2))
            .andExpect(jsonPath("$[0].name").value("김영희"))
            .andExpect(jsonPath("$[1].traineeId").value(trainee1))
            .andExpect(jsonPath("$[1].name").value("홍길동"))
            .andExpect(jsonPath("$[0].phoneNumber").doesNotExist())
    }

    @Test
    fun `없는 id나 다른 박람회의 연수자가 하나라도 있으면 일부만 돌려주지 않고 404이다`() {
        names(EXPO_A, listOf(trainee1, 99999L)).andExpect(status().isNotFound).andExpect(jsonPath("$[0]").doesNotExist())
        names(EXPO_A, listOf(trainee1, traineeOtherExpo)).andExpect(status().isNotFound)
        names(EXPO_A, listOf(traineeOtherExpo)).andExpect(status().isNotFound)
    }

    @Test
    fun `실패하면 호출자가 보낸 id 중 찾지 못한 것만 알려 준다`() {
        names(EXPO_A, listOf(trainee1, 99999L))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value(containsString("99999")))
            .andExpect(jsonPath("$.message").value(not(containsString("홍길동"))))
    }

    @Test
    fun `id 목록이 비었거나 너무 많으면 400이고 상한까지는 크기만으로 거절하지 않는다`() {
        names(EXPO_A, emptyList()).andExpect(status().isBadRequest)
        names(EXPO_A, (1L..10_001L).toList()).andExpect(status().isBadRequest)
        postInternal("/internal/trainees/names", """{"expoId":"$EXPO_A"}""").andExpect(status().isBadRequest)
        // 존재하지 않는 id가 섞여 있으므로 400이 아니라 404여야 한다
        names(EXPO_A, (1L..10_000L).toList()).andExpect(status().isNotFound)
    }

    @Test
    fun `1000명을 넘는 연수자도 한 번에 조회하고 요청 순서를 유지한다`() {
        val ids = (1..2_500).map { save(EXPO_A, "연수자$it", "0102%07d".format(it), "N-%05d".format(it)) }
        val requested = ids.reversed() + trainee1

        names(EXPO_A, requested)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(requested.size))
            .andExpect(jsonPath("$[0].name").value("연수자2500"))
            .andExpect(jsonPath("$[2499].name").value("연수자1"))
            .andExpect(jsonPath("$[2500].traineeId").value(trainee1))
    }

    // --- 인증

    @Test
    fun `토큰이 없거나 틀리면 401이다`() {
        INTERNAL_PATHS.forEach { (path, body) ->
            mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized)
            listOf("wrong-token", "", INTERNAL_TOKEN + "x", INTERNAL_TOKEN.dropLast(1)).forEach { token ->
                mockMvc
                    .perform(post(path).header("X-Internal-Token", token).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized)
            }
        }
    }

    @Test
    fun `관리자 토큰이나 gateway 헤더만으로는 내부 경로에 접근할 수 없다`() {
        INTERNAL_PATHS.forEach { (path, body) ->
            mockMvc
                .perform(post(path).header("Authorization", bearerOf(adminId)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized)
            mockMvc
                .perform(post(path).header("X-User-Id", adminId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized)
        }
    }

    @Test
    fun `내부 토큰은 내부 경로 밖에서는 인증이 되지 않는다`() {
        mockMvc.perform(get("/admin/my").header("X-Internal-Token", INTERNAL_TOKEN)).andExpect(status().isUnauthorized)
    }

    private fun resolve(
        expoId: String,
        trainingId: String,
    ) = postInternal("/internal/trainees/resolve", """{"expoId":"$expoId","trainingId":"$trainingId"}""")

    private fun names(
        expoId: String,
        ids: List<Long>,
    ) = postInternal("/internal/trainees/names", """{"expoId":"$expoId","traineeIds":${ids.joinToString(",", "[", "]")}}""")

    private fun postInternal(
        path: String,
        body: String,
    ): ResultActions =
        mockMvc.perform(
            post(path).header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON).content(body),
        )

    private fun save(
        expoId: String,
        name: String,
        phoneNumber: String,
        trainingId: String,
    ) = traineeRepository
        .save(
            Trainee(
                expoId = expoId,
                name = name,
                phoneNumber = phoneNumber,
                trainingId = trainingId,
                personalInformationStatus = true,
                applicationType = ApplicationType.PRE,
                applicationDate = LocalDateTime.of(2026, 10, 1, 10, 0),
            ),
        ).id!!

    private companion object {
        const val EXPO_A = "0199aaaa-0000-7000-8000-0000000000e1"
        const val EXPO_B = "0199aaaa-0000-7000-8000-0000000000e2"

        val INTERNAL_PATHS =
            listOf(
                "/internal/trainees/resolve" to """{"expoId":"$EXPO_A","trainingId":"T-0001"}""",
                "/internal/trainees/names" to """{"expoId":"$EXPO_A","traineeIds":[1]}""",
            )
    }
}
