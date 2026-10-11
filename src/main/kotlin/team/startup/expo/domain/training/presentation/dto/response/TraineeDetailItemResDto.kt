package team.startup.expo.domain.training.presentation.dto.response

import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.global.dto.InformationResDto

data class TraineeDetailItemResDto(
    val traineeId: Long,
    val name: String,
    val trainingId: String?,
    val phoneNumber: String?,
    // 문자를 받을 번호. 이어 만든 연수자는 본인 번호가 없어 대표자나 참가자의 번호이고, 없으면 phoneNumber다
    val notificationPhoneNumber: String?,
    val personalInformationStatus: Boolean,
    val applicationType: ApplicationType,
    val information: InformationResDto,
)
