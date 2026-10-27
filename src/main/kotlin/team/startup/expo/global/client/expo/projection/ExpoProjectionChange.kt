package team.startup.expo.global.client.expo.projection

import team.startup.expo.global.client.expo.ExpoPeriod

/** 검증을 마친 박람회 변경 이벤트. tombstone이면 [period]가 `null`이고 [sessionIds]가 비어 있다. */
data class ExpoProjectionChange(
    val expoId: String,
    val eventId: String,
    val version: Long,
    val deleted: Boolean,
    val period: ExpoPeriod?,
    val deleting: Boolean,
    val sessionIds: Set<Long>,
)
