package team.startup.expo.domain.participation.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import team.startup.expo.domain.participation.entity.ParticipationType

data class RecordEntryReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    val participationType: ParticipationType,
    @field:NotBlank
    @field:Size(max = 30)
    val phoneNumber: String,
)
