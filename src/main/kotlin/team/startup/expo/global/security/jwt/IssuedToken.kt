package team.startup.expo.global.security.jwt

import java.time.LocalDateTime

data class IssuedToken(
    val value: String,
    val expiresAt: LocalDateTime,
)
