package team.startup.expo.domain.participation.service.impl

import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.presentation.dto.response.GetSurveyAnswerEventResDto
import team.startup.expo.domain.participation.repository.SurveyAnswerEventRepository
import team.startup.expo.domain.participation.service.GetSurveyAnswerEventService
import team.startup.expo.global.exception.ExpectedException

/** 컨슈머가 멱등 처리를 위해 남긴 기록을 그대로 돌려준다. 처리한 적 없는 `eventId`는 404이다. */
@Service
class GetSurveyAnswerEventServiceImpl(
    private val surveyAnswerEventRepository: SurveyAnswerEventRepository,
) : GetSurveyAnswerEventService {
    @Transactional(readOnly = true)
    override fun execute(eventId: String): GetSurveyAnswerEventResDto {
        val event =
            surveyAnswerEventRepository.findByIdOrNull(eventId)
                ?: throw ExpectedException(HttpStatus.NOT_FOUND, "처리한 설문 답변 이벤트를 찾지 못 했습니다.")
        return GetSurveyAnswerEventResDto(eventId = event.eventId, status = event.status, reason = event.reason)
    }
}
