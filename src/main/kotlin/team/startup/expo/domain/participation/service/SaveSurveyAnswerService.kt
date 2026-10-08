package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.SurveyAnswerSubmittedEvent
import team.startup.expo.domain.participation.presentation.dto.response.SurveyAnswerResultEvent

interface SaveSurveyAnswerService {
    /** 삭제 중이거나 삭제된 박람회의 이벤트는 아무것도 저장하지 않고 `null`을 돌려준다. 호출자는 결과를 발행하지 않는다. */
    fun execute(event: SurveyAnswerSubmittedEvent): SurveyAnswerResultEvent?
}
