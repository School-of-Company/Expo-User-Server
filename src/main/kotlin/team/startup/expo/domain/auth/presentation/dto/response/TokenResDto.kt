package team.startup.expo.domain.auth.presentation.dto.response

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime

/**
 * 만료 시각은 UTC 기준의 오프셋 없는 값이고 형식은 노션 명세의 `yyyy-MM-dd'T'HH:mm:ss`다. 클라이언트가 값 뒤에
 * `Z`를 붙여 `new Date(...)`로 파싱하는데, 소수점 초가 길게 붙으면 Safari에서 파싱이 실패할 수 있다.
 *
 * 토큰 값에는 `Bearer `를 붙이지 않는다. 노션 재발급 예시에는 `// Bearer ...` 주석이 있지만 클라이언트가
 * 요청마다 직접 `Bearer `를 붙이므로, 서버가 붙이면 `Bearer Bearer ...`가 된다.
 */
data class TokenResDto(
    val accessToken: String,
    val refreshToken: String,
    @get:JsonFormat(pattern = EXPIRES_IN_FORMAT)
    val accessTokenExpiresIn: LocalDateTime,
    @get:JsonFormat(pattern = EXPIRES_IN_FORMAT)
    val refreshTokenExpiresIn: LocalDateTime,
) {
    override fun toString() =
        "TokenResDto(accessToken=***, refreshToken=***, accessTokenExpiresIn=$accessTokenExpiresIn, refreshTokenExpiresIn=$refreshTokenExpiresIn)"
}

private const val EXPIRES_IN_FORMAT = "yyyy-MM-dd'T'HH:mm:ss"
