package team.startup.expo.domain.auth.service

import team.startup.expo.global.security.jwt.IssuedToken

interface RefreshTokenService {
    /**
     * 새 refresh token을 발급해 저장한다. 관리자는 여러 기기에서 동시에 로그인할 수 있으므로 이미 있는 다른 기기의
     * refresh token은 그대로 둔다. 한 관리자가 가질 수 있는 수(`jwt.max-refresh-tokens`)를 넘으면 가장 오래된 것부터 무효가 된다.
     */
    fun issue(adminId: Long): IssuedToken

    /** 저장된 토큰이면 소유한 관리자 id를, 없거나 만료되었거나 교체되었으면 null을 돌려준다. */
    fun findAdminId(rawToken: String): Long?

    /**
     * 저장된 토큰일 때만 새 토큰으로 교체하고 소유자 id와 새 토큰을 돌려준다. 다른 기기의 토큰은 건드리지 않는다. 확인하는 것과
     * 교체하는 것이 한 번에 처리되므로, 그 사이에 [revoke]가 끼어들어도 폐기된 토큰이 되살아나지 않는다.
     */
    fun rotate(rawToken: String): RotatedRefreshToken?

    /** 관리자의 모든 기기의 refresh token을 폐기한다(전체 로그아웃, 탈퇴). */
    fun revoke(adminId: Long)

    /** 그 관리자의 refresh token 하나만 폐기한다. 이 기기만 로그아웃하고 다른 기기는 유지한다. 없거나 다른 관리자의 토큰이면 아무것도 하지 않는다. */
    fun revokeToken(
        adminId: Long,
        rawToken: String,
    )
}
