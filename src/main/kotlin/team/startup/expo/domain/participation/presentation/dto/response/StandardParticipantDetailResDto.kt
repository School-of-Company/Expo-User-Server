package team.startup.expo.domain.participation.presentation.dto.response

import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.global.dto.InformationResDto

/** [surveyAnswer]는 설문에 답하지 않았으면 `null`이다. 설문이 여럿이면 가장 최근 답변이다. */
data class StandardParticipantDetailResDto(
    val participantId: Long,
    val name: String,
    val phoneNumber: String?,
    val personalInformationStatus: Boolean,
    val applicationType: ApplicationType,
    val information: InformationResDto,
    val surveyAnswer: InformationResDto?,
)
