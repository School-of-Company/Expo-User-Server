package team.startup.expo.projection

/** Expo `docs/expo-change-events.md`의 메시지 모양을 따른 테스트용 `expo.expo.changed` 값. */
object ExpoChangedPayloads {
    const val EXPO = "0199aaaa-0000-7000-8000-0000000000e1"

    fun state(
        eventId: String,
        version: Long,
        expoId: String = EXPO,
        startedDay: String = "2030-01-01",
        finishedDay: String = "2030-01-02",
        deleting: Boolean = false,
        sessions: List<Long> = emptyList(),
        extra: Boolean = false,
    ): String {
        val sessionJson =
            sessions.joinToString(",") {
                """{"id":$it,"title":"회차","startedAt":"2030-01-01T01:00:00Z","endedAt":"2030-01-01T02:00:00Z","revision":1}"""
            }
        val extraJson = if (extra) ""","unknownTopLevel":{"nested":true}""" else ""
        return """{"schemaVersion":1,"eventId":"$eventId","version":$version,"occurredAt":"2030-01-01T00:00:00.123456Z",""" +
            """"expoId":"$expoId","deleted":false,""" +
            """"expo":{"title":"예시 박람회","startedDay":"$startedDay","finishedDay":"$finishedDay","location":"광주",""" +
            """"participantPhoneVerificationRequired":true,"deleting":$deleting},""" +
            """"standardPrograms":[],"trainingPrograms":[],"preregisterSessions":[$sessionJson]$extraJson}"""
    }

    fun tombstone(
        eventId: String,
        version: Long,
        expoId: String = EXPO,
    ): String =
        """{"schemaVersion":1,"eventId":"$eventId","version":$version,"occurredAt":"2030-01-01T00:00:00Z",""" +
            """"expoId":"$expoId","deleted":true}"""
}
