package team.startup.expo.domain.training.presentation.dto.response

import team.startup.expo.global.dto.InformationResDto

data class TraineeDetailResDto(
    val traineeId: Long,
    val expoId: String,
    val name: String,
    val trainingId: String?,
    val information: InformationResDto,
    val participantId: Long?,
    val school: String?,
    val registration: InformationResDto?,
)
