package team.startup.expo.global.client.expo.projection

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Expo가 `expo.expo.changed` 토픽으로 발행하는 박람회 변경 이벤트 중 이 서비스가 읽는 값만 받는다(계약은 Expo의
 * `docs/expo-change-events.md`). 메시지 하나가 그 박람회의 변경 후 전체 상태이고, `deleted=true`면 상태 없는 tombstone이다.
 * 모르는 필드는 무시한다. 검증은 [ExpoChangedEventConsumer]가 한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ExpoChangedEvent(
    val schemaVersion: Int? = null,
    val eventId: String? = null,
    val version: Long? = null,
    val expoId: String? = null,
    val deleted: Boolean? = null,
    val expo: Expo? = null,
    val preregisterSessions: List<PreregisterSession>? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Expo(
        val startedDay: String? = null,
        val finishedDay: String? = null,
        val deleting: Boolean? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PreregisterSession(
        val id: Long? = null,
    )
}
