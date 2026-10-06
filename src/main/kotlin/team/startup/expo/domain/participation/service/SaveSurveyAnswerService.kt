package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.SurveyAnswerSubmittedEvent
import team.startup.expo.domain.participation.presentation.dto.response.SurveyAnswerResultEvent

interface SaveSurveyAnswerService {
    fun execute(event: SurveyAnswerSubmittedEvent): SurveyAnswerResultEvent
}
