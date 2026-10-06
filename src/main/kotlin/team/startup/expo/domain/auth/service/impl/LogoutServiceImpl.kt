package team.startup.expo.domain.auth.service.impl

import org.springframework.stereotype.Service
import team.startup.expo.domain.auth.service.LogoutService
import team.startup.expo.domain.auth.service.RefreshTokenService

@Service
class LogoutServiceImpl(
    private val refreshTokenService: RefreshTokenService,
) : LogoutService {
    override fun execute(adminId: Long) {
        refreshTokenService.revoke(adminId)
    }
}
