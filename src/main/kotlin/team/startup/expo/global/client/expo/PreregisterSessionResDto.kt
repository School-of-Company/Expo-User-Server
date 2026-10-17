package team.startup.expo.global.client.expo

/** Expo의 `GET /internal/expo/{expo_id}/preregister-sessions/{session_id}` 응답 중 이 서비스가 쓰는 값만 받는다. */
data class PreregisterSessionResDto(
    val id: Long,
    val expoId: String,
)
