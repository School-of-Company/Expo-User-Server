package team.startup.expo.domain.training.presentation.dto.response

import team.startup.expo.domain.training.entity.ApplicationType

data class GetTraineeResDto(
    val id: Long,
    val name: String,
    val trainingId: String,
    val phoneNumber: String,
    val applicationType: ApplicationType,
)
