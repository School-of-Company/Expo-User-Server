package team.startup.expo.domain.auth.service

import team.startup.expo.global.security.jwt.IssuedToken

data class RotatedRefreshToken(
    val adminId: Long,
    val token: IssuedToken,
)
