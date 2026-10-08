package team.startup.expo.domain.auth.service

import team.startup.expo.domain.auth.presentation.dto.response.TokenResDto

interface ReissueTokenService {
    /** `RefreshToken` 헤더 값(`Bearer <token>`)으로 새 토큰 쌍을 발급한다. */
    fun execute(refreshToken: String): TokenResDto
}
