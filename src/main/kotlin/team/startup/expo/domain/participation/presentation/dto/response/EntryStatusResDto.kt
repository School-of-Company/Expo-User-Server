package team.startup.expo.domain.participation.presentation.dto.response

import java.time.LocalDate

data class EntryStatusResDto(
    val participantId: Long,
    val attendanceDate: LocalDate,
    val entered: Boolean,
)
