package team.startup.expo.domain.auth.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.auth.presentation.dto.response.TokenResDto
import team.startup.expo.domain.auth.service.RefreshTokenService
import team.startup.expo.domain.auth.service.ReissueTokenService
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.global.exception.ExpectedException

@Service
class ReissueTokenServiceImpl(
    private val adminRepository: AdminRepository,
    private val refreshTokenService: RefreshTokenService,
    private val tokenIssuer: TokenIssuer,
) : ReissueTokenService {
    @Transactional(readOnly = true)
    override fun execute(refreshToken: String): TokenResDto {
        if (!refreshToken.startsWith(BEARER_PREFIX)) throw invalidToken()

        val adminId =
            refreshTokenService.findAdminId(refreshToken.removePrefix(BEARER_PREFIX).trim())
                ?: throw invalidToken()
        val admin =
            adminRepository.findById(adminId).orElse(null)
                ?: run {
                    // 탈퇴한 관리자의 refresh token이 남아 있으면 정리한다
                    refreshTokenService.revoke(adminId)
                    throw invalidToken()
                }

        return tokenIssuer.issue(admin)
    }

    private fun invalidToken() = ExpectedException(HttpStatus.UNAUTHORIZED, "토큰이 만료되었거나 유효하지 않습니다.")

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
