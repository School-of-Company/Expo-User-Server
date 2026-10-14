package team.startup.expo.domain.auth.service

interface LogoutService {
    /** [refreshToken]이 있으면 그 기기만, 없으면 관리자의 모든 기기를 로그아웃한다. */
    fun execute(
        adminId: Long,
        refreshToken: String?,
    )
}
