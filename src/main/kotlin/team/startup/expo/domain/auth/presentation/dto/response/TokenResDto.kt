package team.startup.expo.domain.auth.presentation.dto.response

import java.time.LocalDateTime

/** 만료 시각은 UTC 기준의 오프셋 없는 값이다(클라이언트가 `Z`를 붙여 해석한다). */
data class TokenResDto(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresIn: LocalDateTime,
    val refreshTokenExpiresIn: LocalDateTime,
)
