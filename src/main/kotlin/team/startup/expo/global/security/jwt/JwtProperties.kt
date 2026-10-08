package team.startup.expo.global.security.jwt

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * `privateKey`는 PKCS#8 PEM 형식의 RSA 개인키다. 줄바꿈을 `\n` 문자로 쓴 한 줄 값도 받는다.
 *
 * access token 수명은 [MAX_ACCESS_TOKEN_TTL]을 넘을 수 없다. `Expo` 서비스가 수명이 15분을 넘는 토큰을
 * 거절하므로, 설정 실수로 더 긴 토큰을 발급하면 로그인 직후의 정상 토큰도 `401`이 된다. 넘는 값은 기동 시
 * 거부해서 이 서비스가 그런 토큰을 발급하지 못하게 한다.
 */
@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val privateKey: String,
    val accessTokenTtl: Duration = MAX_ACCESS_TOKEN_TTL,
    val refreshTokenTtl: Duration = Duration.ofDays(7),
) {
    // data class의 기본 toString은 개인키를 그대로 출력한다
    override fun toString() = "JwtProperties(privateKey=***, accessTokenTtl=$accessTokenTtl, refreshTokenTtl=$refreshTokenTtl)"

    init {
        require(accessTokenTtl.isPositive && accessTokenTtl <= MAX_ACCESS_TOKEN_TTL) {
            "jwt.access-token-ttl must be positive and at most $MAX_ACCESS_TOKEN_TTL"
        }
        require(refreshTokenTtl.isPositive) { "jwt.refresh-token-ttl must be positive" }
    }

    companion object {
        val MAX_ACCESS_TOKEN_TTL: Duration = Duration.ofMinutes(15)
    }
}
