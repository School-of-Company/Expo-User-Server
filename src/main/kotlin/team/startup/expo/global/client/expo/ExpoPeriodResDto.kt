package team.startup.expo.global.client.expo

import tools.jackson.databind.JsonNode

/**
 * Expo의 `GET /internal/expo/{expo_id}` 응답. 날짜는 `yyyy-MM-dd` 문자열이다.
 * [features]는 `[{key, configuredEnabled, effectiveEnabled}]`이며 해석은 [ExpoPeriodReader.readFeatures]가 한다. 구형 응답에는 없다.
 */
data class ExpoPeriodResDto(
    val startedDay: String,
    val finishedDay: String,
    val features: JsonNode? = null,
)
