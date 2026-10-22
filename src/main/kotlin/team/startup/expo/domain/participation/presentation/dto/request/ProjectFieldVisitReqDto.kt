package team.startup.expo.domain.participation.presentation.dto.request

import jakarta.validation.constraints.Positive
import java.time.LocalDate
import java.util.UUID

enum class FieldVisitStatus { ACTIVE, CANCELLED }

data class ProjectFieldVisitReqDto(
    val visitDate: LocalDate,
    @field:Positive
    val generation: Long,
    val status: FieldVisitStatus,
    val operationId: UUID,
)
