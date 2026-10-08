package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.response.GetSurveyAnswerEventResDto

interface GetSurveyAnswerEventService {
    fun execute(eventId: String): GetSurveyAnswerEventResDto
}
