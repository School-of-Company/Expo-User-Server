package team.startup.expo.domain.participation.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class GetEntryStatusReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    @field:Positive
    val participantId: Long,
    val attendanceDate: LocalDate,
)
