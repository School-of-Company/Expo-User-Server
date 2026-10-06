package team.startup.expo.domain.participation.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import team.startup.expo.domain.participation.entity.ParticipationType

data class ResolveParticipantReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    @field:NotBlank
    @field:Size(max = 30)
    val phoneNumber: String,
    val participationType: ParticipationType,
)
