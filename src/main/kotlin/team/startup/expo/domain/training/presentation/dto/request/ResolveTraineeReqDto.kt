package team.startup.expo.domain.training.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ResolveTraineeReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    @field:NotBlank
    @field:Size(max = 15)
    val trainingId: String,
)
