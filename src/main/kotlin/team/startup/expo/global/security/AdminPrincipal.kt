package team.startup.expo.global.security

import team.startup.expo.domain.user.entity.Authority

data class AdminPrincipal(
    val id: Long,
    val authority: Authority,
)
