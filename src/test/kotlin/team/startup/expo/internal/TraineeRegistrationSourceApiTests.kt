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
import team.startup.expo.domain.participation.entity.Occupation
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.domain.training.entity.Trainee
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.support.IntegrationTestSupport
import java.time.LocalDateTime

/** 연결된 연수자의 목록과 단건 상세가 연결된 참가자의 등록 답변 원천을 따로 돌려주는지 확인한다. */
class TraineeRegistrationSourceApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var standardParticipantRepository: StandardParticipantRepository

    @Autowired
    private lateinit var traineeRepository: TraineeRepository

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_expo_deletion, tb_trainee, tb_standard_participant RESTART IDENTITY CASCADE")
    }

    @Test
    fun `이어진 연수자의 목록과 단건은 연결된 참가자의 등록 답변과 문항 스냅샷을 registration으로 돌려준다`() {
        val representative = participant("대표", PHONE, info = ANSWERS, questions = QUESTIONS)
        val traineeId = link(representative, school = "광주고")

        details(EXPO)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].traineeId").value(traineeId))
            .andExpect(jsonPath("$.items[0].participantId").value(representative))
            .andExpect(jsonPath("$.items[0].school").value("광주고"))
            .andExpect(jsonPath("$.items[0].registration.answers.소속").value("광주고"))
            .andExpect(jsonPath("$.items[0].registration.questions[0].title").value("소속"))
            .andExpect(jsonPath("$.items[0].phoneNumber").doesNotExist())
            .andExpect(jsonPath("$.items[0].notificationPhoneNumber").value(PHONE))
        one(traineeId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participantId").value(representative))
            .andExpect(jsonPath("$.school").value("광주고"))
            .andExpect(jsonPath("$.registration.answers.소속").value("광주고"))
            .andExpect(jsonPath("$.registration.questions[0].title").value("소속"))
    }

    @Test
    fun `연수자 자신의 information과 등록 원천을 섞지 않는다`() {
        val representative = participant("대표", PHONE, info = ANSWERS, questions = QUESTIONS)
        val traineeId = link(representative)

        // 이어 만든 연수자는 자신의 신청 답변이 없고 등록 답변은 registration에만 있다
        one(traineeId)
            .andExpect(jsonPath("$.information.answers.소속").doesNotExist())
            .andExpect(jsonPath("$.registration.answers.소속").value("광주고"))
    }

    @Test
    fun `스냅샷 없이 저장된 참가자의 연수자는 registration이 null이다`() {
        val representative = participant("대표", PHONE, info = ANSWERS, questions = QUESTIONS)
        val companion = participant("동행", null, representativeId = representative)
        val traineeId = link(companion)

        details(EXPO)
            .andExpect(jsonPath("$.items[0].participantId").value(companion))
            .andExpect(jsonPath("$.items[0].registration").doesNotExist())
        one(traineeId).andExpect(jsonPath("$.registration").doesNotExist())
    }

    @Test
    fun `기존 연수자는 연결과 등록 원천이 null이고 자신의 information이 그대로이다`() {
        val traineeId = legacyTrainee("구형", "01011112222", info = """{"소속":"빛고을초"}""")

        details(EXPO)
            .andExpect(jsonPath("$.items[0].traineeId").value(traineeId))
            .andExpect(jsonPath("$.items[0].participantId").doesNotExist())
            .andExpect(jsonPath("$.items[0].registration").doesNotExist())
            .andExpect(jsonPath("$.items[0].information.answers.소속").value("빛고을초"))
        one(traineeId)
            .andExpect(jsonPath("$.participantId").doesNotExist())
            .andExpect(jsonPath("$.registration").doesNotExist())
            .andExpect(jsonPath("$.information.answers.소속").value("빛고을초"))
    }

    @Test
    fun `연결된 연수자와 기존 연수자가 섞인 페이지도 각각 맞게 돌려주고 커서로 이어진다`() {
        val representative = participant("대표", PHONE, info = ANSWERS, questions = QUESTIONS)
        val legacy = legacyTrainee("구형", "01011112222", info = """{"소속":"빛고을초"}""")
        val linked = link(representative)
        val second = participant("대표2", "01033334444", info = """{"소속":"전남고"}""", questions = QUESTIONS)
        val linkedSecond = link(second)

        details(EXPO, size = 3)
            .andExpect(jsonPath("$.items.length()").value(3))
            .andExpect(jsonPath("$.items[0].traineeId").value(legacy))
            .andExpect(jsonPath("$.items[0].registration").doesNotExist())
            .andExpect(jsonPath("$.items[1].traineeId").value(linked))
            .andExpect(jsonPath("$.items[1].registration.answers.소속").value("광주고"))
            .andExpect(jsonPath("$.items[2].traineeId").value(linkedSecond))
            .andExpect(jsonPath("$.items[2].registration.answers.소속").value("전남고"))
            .andExpect(jsonPath("$.nextCursor").doesNotExist())
    }

    @Test
    fun `다른 박람회의 참가자는 등록 원천으로 쓰지 않는다`() {
        val other = participant("다른행사", "01099990000", info = ANSWERS, questions = QUESTIONS, expoId = OTHER_EXPO)
        val traineeId = link(other, expoId = OTHER_EXPO)
        // 연수자만 다른 박람회로 옮긴 상태(연결된 참가자는 그대로)를 흉내 낸다
        jdbcTemplate.update("UPDATE tb_trainee SET expo_id = ? WHERE id = ?", EXPO, traineeId)

        details(EXPO)
            .andExpect(jsonPath("$.items[0].traineeId").value(traineeId))
            .andExpect(jsonPath("$.items[0].participantId").value(other))
            .andExpect(jsonPath("$.items[0].registration").doesNotExist())
        one(traineeId).andExpect(jsonPath("$.registration").doesNotExist())
    }

    @Test
    fun `토큰이 없거나 틀리면 두 경로 모두 401이다`() {
        val traineeId = link(participant("대표", PHONE, info = ANSWERS, questions = QUESTIONS))

        mockMvc.perform(get("/internal/expos/$EXPO/trainees/details")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/internal/trainees/$traineeId/details")).andExpect(status().isUnauthorized)
        mockMvc
            .perform(get("/internal/trainees/$traineeId/details").header("X-Internal-Token", "wrong"))
            .andExpect(status().isUnauthorized)
    }

    private fun details(
        expoId: String,
        size: Int? = null,
    ): ResultActions {
        val request = get("/internal/expos/$expoId/trainees/details").header("X-Internal-Token", INTERNAL_TOKEN)
        size?.let { request.param("size", it.toString()) }
        return mockMvc.perform(request)
    }

    private fun one(traineeId: Long): ResultActions =
        mockMvc.perform(get("/internal/trainees/$traineeId/details").header("X-Internal-Token", INTERNAL_TOKEN))

    private fun link(
        participantId: Long,
        expoId: String = EXPO,
        school: String? = null,
    ): Long {
        val schoolField = school?.let { ""","school":"$it"""" }.orEmpty()
        val body = """{"expoId":"$expoId","participantId":$participantId$schoolField}"""
        return mockMvc
            .perform(
                post("/internal/trainees/resolve-or-create-by-participant")
                    .header("X-Internal-Token", INTERNAL_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isOk)
            .andReturn()
            .response.contentAsString
            .substringAfter("\"traineeId\":")
            .substringBefore(',')
            .toLong()
    }

    private fun legacyTrainee(
        name: String,
        phone: String,
        info: String,
    ): Long =
        traineeRepository
            .save(
                Trainee(
                    expoId = EXPO,
                    name = name,
                    phoneNumber = phone,
                    trainingId = "T-0001",
                    informationJson = info,
                    personalInformationStatus = true,
                    applicationType = ApplicationType.PRE,
                    applicationDate = LocalDateTime.of(2026, 10, 1, 10, 0),
                ),
            ).id!!

    private fun participant(
        name: String,
        phone: String?,
        info: String? = null,
        questions: String? = null,
        representativeId: Long? = null,
        expoId: String = EXPO,
    ): Long =
        standardParticipantRepository
            .save(
                StandardParticipant(
                    expoId = expoId,
                    name = name,
                    phoneNumber = phone,
                    informationJson = info,
                    informationQuestions = questions,
                    personalInformationStatus = true,
                    applicationType = ApplicationType.PRE,
                    applicationDate = LocalDateTime.of(2026, 10, 1, 10, 0),
                    occupation = Occupation.TEACHER,
                    school = "광주고",
                    representativeId = representativeId,
                ),
            ).id!!

    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-000000000b01"
        const val OTHER_EXPO = "0199aaaa-0000-7000-8000-000000000b02"
        const val PHONE = "01012345678"
        const val ANSWERS = """{"소속":"광주고"}"""
        const val QUESTIONS = """[{"id":"q1","title":"소속","order":0,"formType":"SENTENCE"}]"""
    }
}
