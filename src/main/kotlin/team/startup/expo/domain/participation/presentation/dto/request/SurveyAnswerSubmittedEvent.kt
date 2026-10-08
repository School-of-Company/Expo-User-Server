package team.startup.expo.domain.participation.presentation.dto.request

import team.startup.expo.domain.participation.entity.ParticipationType
import tools.jackson.databind.JsonNode

/**
 * Form 서비스가 `survey.answer.submit` 토픽으로 발행하는 답변 접수 이벤트.
 * `answerJson`은 Form이 검증을 마친 답변을 문자열로 직렬화한 값이며 해석 없이 저장한다.
 * `version` 1과 2를 받는다. 2는 제출 당시 문항 스냅샷(`questions`)이 더해진 것이고, 1은 스냅샷 없이 저장한다.
 */
data class SurveyAnswerSubmittedEvent(
    val eventId: String,
    val version: Int,
    val surveyId: String,
    val expoId: String,
    val participationType: ParticipationType,
    val phoneNumber: String,
    val answerJson: String,
    val personalInformationStatus: Boolean,
    /** v2부터 실리는 제출 당시 설문 문항 스냅샷. v1 이벤트에는 없어 `null`이다. */
    val questions: JsonNode? = null,
)
