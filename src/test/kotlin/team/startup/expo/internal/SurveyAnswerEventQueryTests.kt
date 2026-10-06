package team.startup.expo.internal

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.participation.entity.SurveyAnswerEvent
import team.startup.expo.domain.participation.entity.SurveyAnswerStatus
import team.startup.expo.domain.participation.repository.SurveyAnswerEventRepository
import team.startup.expo.support.IntegrationTestSupport

class SurveyAnswerEventQueryTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var surveyAnswerEventRepository: SurveyAnswerEventRepository

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_survey_answer_event")
        surveyAnswerEventRepository.save(event(STORED_EVENT, SurveyAnswerStatus.STORED, null))
        surveyAnswerEventRepository.save(event(REJECTED_EVENT, SurveyAnswerStatus.REJECTED, "응답자를 찾지 못 했습니다."))
    }

    @Test
    fun `저장한 이벤트는 STORED를 돌려준다`() {
        query(STORED_EVENT)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.eventId").value(STORED_EVENT))
            .andExpect(jsonPath("$.status").value("STORED"))
            .andExpect(jsonPath("$.reason").doesNotExist())
    }

    @Test
    fun `거절한 이벤트는 REJECTED와 사유를 돌려준다`() {
        query(REJECTED_EVENT)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.reason").value("응답자를 찾지 못 했습니다."))
    }

    @Test
    fun `처리한 적 없는 eventId는 404이다`() {
        query("0199aaaa-0000-7000-8000-00000000ffff").andExpect(status().isNotFound)
    }

    @Test
    fun `토큰이 없거나 틀리거나 관리자 토큰만 있으면 401이다`() {
        mockMvc.perform(get("/internal/survey-answer-events/$STORED_EVENT")).andExpect(status().isUnauthorized)
        mockMvc
            .perform(get("/internal/survey-answer-events/$STORED_EVENT").header("X-Internal-Token", "wrong"))
            .andExpect(status().isUnauthorized)
        mockMvc
            .perform(get("/internal/survey-answer-events/$STORED_EVENT").header("Authorization", bearerOf(1L)))
            .andExpect(status().isUnauthorized)
    }

    private fun query(eventId: String): ResultActions =
        mockMvc.perform(get("/internal/survey-answer-events/$eventId").header("X-Internal-Token", INTERNAL_TOKEN))

    private fun event(
        eventId: String,
        status: SurveyAnswerStatus,
        reason: String?,
    ) = SurveyAnswerEvent(eventId = eventId, surveyId = SURVEY_ID, expoId = EXPO_ID, status = status, reason = reason)

    private companion object {
        const val STORED_EVENT = "0199aaaa-0000-7000-8000-000000000001"
        const val REJECTED_EVENT = "0199aaaa-0000-7000-8000-000000000002"
        const val SURVEY_ID = "5d1c7f4e-0000-4000-8000-000000000001"
        const val EXPO_ID = "0199aaaa-0000-7000-8000-0000000000aa"
    }
}
