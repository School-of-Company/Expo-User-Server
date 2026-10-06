package team.startup.expo.domain.participation.presentation.dto.request

import team.startup.expo.domain.participation.entity.ParticipationType

/**
 * Form 서비스가 `survey.answer.submit` 토픽으로 발행하는 답변 접수 이벤트.
 * `answerJson`은 Form이 검증을 마친 답변을 문자열로 직렬화한 값이며 해석 없이 저장한다.
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
)
