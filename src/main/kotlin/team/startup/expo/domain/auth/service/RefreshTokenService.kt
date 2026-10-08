package team.startup.expo.domain.auth.service

import team.startup.expo.global.security.jwt.IssuedToken

interface RefreshTokenService {
    /**
     * 새 refresh token을 발급해 저장한다. 관리자당 하나이므로 이미 있으면 교체한다.
     *
     * 단일 기기 로그인 정책이다(v1과 같다). 다른 기기에서 로그인하면 먼저 로그인한 기기의 refresh token이
     * 즉시 무효가 되어, 그 기기는 access token이 만료되는 시점(최대 15분 뒤)에 재발급에 실패하고 다시
     * 로그인해야 한다. 다중 기기 로그인이 필요해지면 세션별로 저장하도록 바꿔야 한다.
     */
    fun issue(adminId: Long): IssuedToken

    /** 저장된 토큰이면 소유한 관리자 id를, 없거나 만료되었거나 교체되었으면 null을 돌려준다. */
    fun findAdminId(rawToken: String): Long?

    /**
     * 저장된 토큰과 같을 때만 새 토큰으로 교체하고 소유자 id와 새 토큰을 돌려준다. 같은지 확인하는 것과
     * 교체하는 것이 한 번에 처리되므로, 그 사이에 [revoke]가 끼어들어도 폐기된 토큰이 되살아나지 않는다.
     */
    fun rotate(rawToken: String): RotatedRefreshToken?

    fun revoke(adminId: Long)
}
