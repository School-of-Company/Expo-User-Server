package team.startup.expo.domain.auth.service.impl

import org.springframework.stereotype.Component
import team.startup.expo.domain.auth.presentation.dto.response.TokenResDto
import team.startup.expo.domain.auth.service.RefreshTokenService
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.global.security.jwt.JwtProvider

/** 로그인과 재발급이 같은 방식으로 토큰 쌍을 만들도록 모은다. refresh token은 새로 저장되며 이전 값을 교체한다. */
@Component
class TokenIssuer(
    private val jwtProvider: JwtProvider,
    private val refreshTokenService: RefreshTokenService,
) {
    fun issue(admin: Admin): TokenResDto {
        val adminId = requireNotNull(admin.id)
        val accessToken = jwtProvider.generateAccessToken(adminId, admin.authority)
        val refreshToken = refreshTokenService.issue(adminId)
        return TokenResDto(
            accessToken = accessToken.value,
            refreshToken = refreshToken.value,
            accessTokenExpiresIn = accessToken.expiresAt,
            refreshTokenExpiresIn = refreshToken.expiresAt,
        )
    }
}
