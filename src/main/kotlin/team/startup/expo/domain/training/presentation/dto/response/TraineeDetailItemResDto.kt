package team.startup.expo.domain.training.presentation.dto.response

import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.global.dto.InformationResDto

data class TraineeDetailItemResDto(
    val traineeId: Long,
    val name: String,
    val trainingId: String,
    val phoneNumber: String,
    val personalInformationStatus: Boolean,
    val applicationType: ApplicationType,
    val information: InformationResDto,
)
