package team.startup.expo.global.client.expo

import java.time.Instant

data class StaffInviteExchangeResDto(
    val inviteId: Long,
    val expoId: String,
    val expiresAt: Instant,
)
