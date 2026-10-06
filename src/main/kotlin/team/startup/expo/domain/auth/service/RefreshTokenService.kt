package team.startup.expo.domain.auth.service

import team.startup.expo.global.security.jwt.IssuedToken

interface RefreshTokenService {
    /** 새 refresh token을 발급해 저장한다. 이미 있으면 교체한다. */
    fun issue(adminId: Long): IssuedToken

    /** 저장된 토큰이면 소유한 관리자 id를, 없거나 만료되었으면 null을 돌려준다. */
    fun findAdminId(rawToken: String): Long?

    fun revoke(adminId: Long)
}
