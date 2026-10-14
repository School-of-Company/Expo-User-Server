package team.startup.expo.domain.auth.service.impl

import org.springframework.stereotype.Service
import team.startup.expo.domain.auth.service.LogoutService
import team.startup.expo.domain.auth.service.RefreshTokenService

@Service
class LogoutServiceImpl(
    private val refreshTokenService: RefreshTokenService,
) : LogoutService {
    override fun execute(
        adminId: Long,
        refreshToken: String?,
    ) {
        val rawToken = refreshToken?.removePrefix(BEARER_PREFIX)?.trim()?.takeIf { it.isNotEmpty() }
        if (rawToken == null) {
            refreshTokenService.revoke(adminId)
        } else {
            refreshTokenService.revokeToken(adminId, rawToken)
        }
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
