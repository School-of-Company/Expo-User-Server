package team.startup.expo.global.client.expo

import java.time.LocalDate

data class ExpoPeriod(
    val startedDay: LocalDate,
    val finishedDay: LocalDate,
) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(startedDay) && !date.isAfter(finishedDay)
}
