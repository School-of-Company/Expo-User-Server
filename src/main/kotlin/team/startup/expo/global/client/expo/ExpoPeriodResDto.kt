package team.startup.expo.global.client.expo

/** Expo의 `GET /internal/expo/{expo_id}` 응답. 날짜는 `yyyy-MM-dd` 문자열이다. */
data class ExpoPeriodResDto(
    val startedDay: String,
    val finishedDay: String,
)
