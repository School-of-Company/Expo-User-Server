package team.startup.expo.domain.participation.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class GetAdminCancellationStatusReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    @field:NotNull
    val participantId: Long?,
)
