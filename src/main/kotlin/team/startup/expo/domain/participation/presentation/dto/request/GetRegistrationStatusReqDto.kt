package team.startup.expo.domain.participation.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class GetRegistrationStatusReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    @field:NotBlank
    @field:Size(max = 100)
    val requestId: String,
)
