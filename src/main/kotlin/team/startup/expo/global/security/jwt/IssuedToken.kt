package team.startup.expo.global.security.jwt

import java.time.LocalDateTime

/**
 * `expiresAt`은 UTC 기준의 오프셋 없는 시각이다. 클라이언트가 값 뒤에 `Z`를 붙여 UTC로 해석하므로
 * 서버 시간대와 무관하게 UTC로 맞춘다.
 */
data class IssuedToken(
    val value: String,
    val expiresAt: LocalDateTime,
)
