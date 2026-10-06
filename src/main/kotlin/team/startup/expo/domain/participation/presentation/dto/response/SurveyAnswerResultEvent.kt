package team.startup.expo.domain.participation.presentation.dto.response

import team.startup.expo.domain.participation.entity.SurveyAnswerStatus

/** Form 서비스가 `survey.answer.result` 토픽에서 받는 처리 결과. `reason`은 `REJECTED`일 때만 채운다. */
data class SurveyAnswerResultEvent(
    val eventId: String,
    val status: SurveyAnswerStatus,
    val reason: String? = null,
)
