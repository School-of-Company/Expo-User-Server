package team.startup.expo.domain.auth.service.impl

import org.springframework.stereotype.Component
import team.startup.expo.domain.auth.presentation.dto.response.TokenResDto
import team.startup.expo.domain.auth.service.RefreshTokenService
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.global.security.jwt.IssuedToken
import team.startup.expo.global.security.jwt.JwtProvider

/** 로그인과 재발급이 같은 방식으로 토큰 쌍 응답을 만들도록 모은다. */
@Component
class TokenIssuer(
    private val jwtProvider: JwtProvider,
    private val refreshTokenService: RefreshTokenService,
) {
    /** 새 refresh token을 발급(기존 값 교체)해 토큰 쌍을 만든다. */
    fun issue(admin: Admin): TokenResDto = withRefreshToken(admin, refreshTokenService.issue(requireNotNull(admin.id)))

    /** 이미 발급된 refresh token에 새 access token을 붙여 토큰 쌍을 만든다. */
    fun withRefreshToken(
        admin: Admin,
        refreshToken: IssuedToken,
    ): TokenResDto {
        val accessToken = jwtProvider.generateAccessToken(requireNotNull(admin.id), admin.authority)
        return TokenResDto(
            accessToken = accessToken.value,
            refreshToken = refreshToken.value,
            accessTokenExpiresIn = accessToken.expiresAt,
            refreshTokenExpiresIn = refreshToken.expiresAt,
        )
    }
}
