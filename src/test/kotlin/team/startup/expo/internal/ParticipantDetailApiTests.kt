package team.startup.expo.internal

import org.hamcrest.Matchers.nullValue
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
import team.startup.expo.support.IntegrationTestSupport
import java.time.LocalDateTime

class ParticipantDetailApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var standardParticipantRepository: StandardParticipantRepository

    @Autowired
    private lateinit var traineeRepository: TraineeRepository

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_trainee, tb_standard_participant RESTART IDENTITY CASCADE")
    }

    // --- GET /internal/expos/{expoId}/standard-participants/details

    @Test
    fun `행사의 일반 참가자를 id 오름차순으로 신청 답변과 함께 돌려준다`() {
        val first = standard(EXPO, "010-0000-0001", "홍길동", info = """{"이름":"홍길동","학교":"광주"}""", type = ApplicationType.PRE, agree = true)
        val second = standard(EXPO, "01000000002", "김영희", info = null, type = ApplicationType.FIELD, agree = false)
        standard(OTHER_EXPO, "01000000003", "다른행사")

        standardDetails(EXPO)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.nextCursor").value(nullValue()))
            .andExpect(jsonPath("$.items[0].participantId").value(first))
            .andExpect(jsonPath("$.items[0].name").value("홍길동"))
            .andExpect(jsonPath("$.items[0].phoneNumber").value("010-0000-0001"))
            .andExpect(jsonPath("$.items[0].personalInformationStatus").value(true))
            .andExpect(jsonPath("$.items[0].applicationType").value("PRE"))
            .andExpect(jsonPath("$.items[0].information.answers.이름").value("홍길동"))
            .andExpect(jsonPath("$.items[0].information.answers.학교").value("광주"))
            .andExpect(jsonPath("$.items[0].information.questions").value(nullValue()))
            .andExpect(jsonPath("$.items[0].surveyAnswer").value(nullValue()))
            .andExpect(jsonPath("$.items[1].participantId").value(second))
            .andExpect(jsonPath("$.items[1].applicationType").value("FIELD"))
            .andExpect(jsonPath("$.items[1].personalInformationStatus").value(false))
            // 신청 답변이 없으면 빈 객체이다
            .andExpect(jsonPath("$.items[1].information.answers").isEmpty)
    }

    @Test
    fun `설문 답변이 있으면 함께 주고 설문이 여럿이면 가장 최근 답변을 준다`() {
        val answered = standard(EXPO, "01000000001", "응답함")
        val unanswered = standard(EXPO, "01000000002", "응답안함")
        surveyAnswer(answered, "11111111-0000-4000-8000-000000000001", """{"1":"예전"}""")
        surveyAnswer(answered, "11111111-0000-4000-8000-000000000002", """{"1":"최근","2":"b"}""")

        standardDetails(EXPO)
            .andExpect(jsonPath("$.items[?(@.participantId==$answered)].surveyAnswer.answers.1").value("최근"))
            .andExpect(jsonPath("$.items[?(@.participantId==$answered)].surveyAnswer.questions").value(nullValue()))
            .andExpect(jsonPath("$.items[?(@.participantId==$unanswered)].surveyAnswer").value(nullValue()))
    }

    @Test
    fun `페이지 경계에서 nextCursor로 이어 읽고 마지막 페이지는 null이다`() {
        val ids = (1..5).map { standard(EXPO, "0100000000$it", "참가자$it") }

        standardDetails(EXPO, size = 2)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.nextCursor").value(ids[1]))
        standardDetails(EXPO, cursor = ids[1], size = 2)
            .andExpect(jsonPath("$.items[0].participantId").value(ids[2]))
            .andExpect(jsonPath("$.nextCursor").value(ids[3]))
        standardDetails(EXPO, cursor = ids[3], size = 2)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].participantId").value(ids[4]))
            .andExpect(jsonPath("$.nextCursor").value(nullValue()))
    }

    @Test
    fun `정확히 size개면 마지막 페이지이고 빈 페이지를 더 주지 않는다`() {
        repeat(4) { standard(EXPO, "0100000000$it", "참가자$it") }

        standardDetails(EXPO, size = 4)
            .andExpect(jsonPath("$.items.length()").value(4))
            .andExpect(jsonPath("$.nextCursor").value(nullValue()))
    }

    @Test
    fun `참가자가 없는 행사는 빈 목록이다`() {
        standardDetails(EXPO)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(0))
            .andExpect(jsonPath("$.nextCursor").value(nullValue()))
    }

    @Test
    fun `잘못된 cursor나 size는 400이다`() {
        standardDetails(EXPO, size = 0).andExpect(status().isBadRequest)
        standardDetails(EXPO, size = 501).andExpect(status().isBadRequest)
        standardDetails(EXPO, cursor = -1).andExpect(status().isBadRequest)
        standardDetails(EXPO, size = 500).andExpect(status().isOk)
    }

    // --- GET /internal/expos/{expoId}/trainees/details

    @Test
    fun `행사의 연수자를 id 오름차순으로 신청 답변과 함께 돌려준다`() {
        val first = trainee(EXPO, "01011110001", "T-1", "김연수", info = """{"소속":"빛고을초"}""")
        val second = trainee(EXPO, "01011110002", "T-2", "이연수", info = null, type = ApplicationType.FIELD)
        trainee(OTHER_EXPO, "01011110003", "T-3", "다른행사")

        traineeDetails(EXPO)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.nextCursor").value(nullValue()))
            .andExpect(jsonPath("$.items[0].traineeId").value(first))
            .andExpect(jsonPath("$.items[0].name").value("김연수"))
            .andExpect(jsonPath("$.items[0].trainingId").value("T-1"))
            .andExpect(jsonPath("$.items[0].phoneNumber").value("01011110001"))
            .andExpect(jsonPath("$.items[0].personalInformationStatus").value(true))
            .andExpect(jsonPath("$.items[0].applicationType").value("PRE"))
            .andExpect(jsonPath("$.items[0].information.answers.소속").value("빛고을초"))
            .andExpect(jsonPath("$.items[0].information.questions").value(nullValue()))
            .andExpect(jsonPath("$.items[1].traineeId").value(second))
            .andExpect(jsonPath("$.items[1].applicationType").value("FIELD"))
            .andExpect(jsonPath("$.items[1].information.answers").isEmpty)
    }

    @Test
    fun `연수자도 nextCursor로 이어 읽고 빈 행사는 빈 목록이다`() {
        val ids = (1..3).map { trainee(EXPO, "0101111000$it", "T-$it", "연수자$it") }

        traineeDetails(EXPO, size = 2).andExpect(jsonPath("$.nextCursor").value(ids[1]))
        traineeDetails(EXPO, cursor = ids[1], size = 2)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.nextCursor").value(nullValue()))
        traineeDetails(OTHER_EXPO)
            .andExpect(jsonPath("$.items.length()").value(0))
            .andExpect(jsonPath("$.nextCursor").value(nullValue()))
        traineeDetails(EXPO, size = 501).andExpect(status().isBadRequest)
    }

    // --- GET /internal/trainees/{traineeId}/details

    @Test
    fun `연수자 한 명의 상세를 돌려주고 없으면 404이다`() {
        val id = trainee(EXPO, "01011110001", "T-1", "김연수", info = """{"소속":"빛고을초"}""")

        mockMvc
            .perform(get("/internal/trainees/$id/details").header("X-Internal-Token", INTERNAL_TOKEN))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.traineeId").value(id))
            .andExpect(jsonPath("$.expoId").value(EXPO))
            .andExpect(jsonPath("$.name").value("김연수"))
            .andExpect(jsonPath("$.trainingId").value("T-1"))
            .andExpect(jsonPath("$.information.answers.소속").value("빛고을초"))
            .andExpect(jsonPath("$.phoneNumber").doesNotExist())
        mockMvc
            .perform(get("/internal/trainees/${id + 1000}/details").header("X-Internal-Token", INTERNAL_TOKEN))
            .andExpect(status().isNotFound)
    }

    // --- POST /internal/standard-participants/details

    @Test
    fun `일괄 조회는 요청 순서대로 돌려주고 중복 id는 한 번만 준다`() {
        val a = standard(EXPO, "01000000001", "가")
        val b = standard(EXPO, "01000000002", "나", agree = false)
        val c = standard(EXPO, "01000000003", "다")

        briefs(EXPO, listOf(c, a, b, a))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].participantId").value(c))
            .andExpect(jsonPath("$[1].participantId").value(a))
            .andExpect(jsonPath("$[2].participantId").value(b))
            .andExpect(jsonPath("$[2].name").value("나"))
            .andExpect(jsonPath("$[2].phoneNumber").value("01000000002"))
            .andExpect(jsonPath("$[2].personalInformationStatus").value(false))
            .andExpect(jsonPath("$[0].information").doesNotExist())
    }

    @Test
    fun `일괄 조회에 없는 id나 다른 박람회의 id가 섞이면 조용히 빼지 않고 404이다`() {
        val a = standard(EXPO, "01000000001", "가")
        val other = standard(OTHER_EXPO, "01000000002", "다른행사")

        briefs(EXPO, listOf(a, a + 1000)).andExpect(status().isNotFound)
        briefs(EXPO, listOf(a, other)).andExpect(status().isNotFound)
    }

    @Test
    fun `일괄 조회의 잘못된 요청은 400이다`() {
        briefs(EXPO, emptyList()).andExpect(status().isBadRequest)
        briefs(EXPO, (1L..10_001L).toList()).andExpect(status().isBadRequest)
    }

    // --- 인증

    @Test
    fun `토큰이 없거나 틀리거나 관리자 토큰만 있으면 모두 401이다`() {
        val id = standard(EXPO, "01000000001", "가")
        val requests =
            listOf(
                get("/internal/expos/$EXPO/standard-participants/details"),
                get("/internal/expos/$EXPO/trainees/details"),
                get("/internal/trainees/$id/details"),
                post("/internal/standard-participants/details")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"expoId":"$EXPO","participantIds":[$id]}"""),
            )
        requests.forEach { request ->
            mockMvc.perform(request).andExpect(status().isUnauthorized)
        }
        mockMvc
            .perform(get("/internal/expos/$EXPO/standard-participants/details").header("X-Internal-Token", "wrong"))
            .andExpect(status().isUnauthorized)
        mockMvc
            .perform(get("/internal/expos/$EXPO/standard-participants/details").header("Authorization", bearerOf(1L)))
            .andExpect(status().isUnauthorized)
    }

    private fun standardDetails(
        expoId: String,
        cursor: Long? = null,
        size: Int? = null,
    ): ResultActions = details("/internal/expos/$expoId/standard-participants/details", cursor, size)

    private fun traineeDetails(
        expoId: String,
        cursor: Long? = null,
        size: Int? = null,
    ): ResultActions = details("/internal/expos/$expoId/trainees/details", cursor, size)

    private fun details(
        path: String,
        cursor: Long?,
        size: Int?,
    ): ResultActions {
        val request = get(path).header("X-Internal-Token", INTERNAL_TOKEN)
        cursor?.let { request.param("cursor", it.toString()) }
        size?.let { request.param("size", it.toString()) }
        return mockMvc.perform(request)
    }

    private fun briefs(
        expoId: String,
        ids: List<Long>,
    ): ResultActions =
        mockMvc.perform(
            post("/internal/standard-participants/details")
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"expoId":"$expoId","participantIds":[${ids.joinToString(",")}]}"""),
        )

    private fun standard(
        expoId: String,
        phone: String,
        name: String,
        info: String? = null,
        type: ApplicationType = ApplicationType.PRE,
        agree: Boolean = true,
    ): Long =
        standardParticipantRepository
            .save(
                StandardParticipant(
                    expoId = expoId,
                    name = name,
                    phoneNumber = phone,
                    informationJson = info,
                    personalInformationStatus = agree,
                    applicationType = type,
                    applicationDate = LocalDateTime.of(2026, 10, 1, 10, 0),
                ),
            ).id!!

    private fun trainee(
        expoId: String,
        phone: String,
        trainingId: String,
        name: String,
        info: String? = null,
        type: ApplicationType = ApplicationType.PRE,
    ): Long =
        traineeRepository
            .save(
                Trainee(
                    expoId = expoId,
                    name = name,
                    phoneNumber = phone,
                    trainingId = trainingId,
                    informationJson = info,
                    personalInformationStatus = true,
                    applicationType = type,
                    applicationDate = LocalDateTime.of(2026, 10, 1, 10, 0),
                ),
            ).id!!

    private fun surveyAnswer(
        participantId: Long,
        surveyId: String,
        answerJson: String,
    ) {
        jdbcTemplate.update(
            "INSERT INTO tb_standard_participant_survey_answer " +
                "(survey_id, standard_participant_id, answer_json, personal_information_status) VALUES (?, ?, ?::jsonb, true)",
            surveyId,
            participantId,
            answerJson,
        )
    }

    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-0000000000d1"
        const val OTHER_EXPO = "0199aaaa-0000-7000-8000-0000000000d2"
    }
}
