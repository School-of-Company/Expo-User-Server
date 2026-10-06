package team.startup.expo.domain.participation.presentation.dto.response

import team.startup.expo.domain.participation.entity.SurveyAnswerStatus

/** 처리를 마친 설문 답변 이벤트의 결과. `reason`은 `REJECTED`일 때만 채운다. */
data class GetSurveyAnswerEventResDto(
    val eventId: String,
    val status: SurveyAnswerStatus,
    val reason: String?,
)
