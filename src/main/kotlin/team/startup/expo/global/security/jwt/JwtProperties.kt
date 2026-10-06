package team.startup.expo.global.security.jwt

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * `privateKey`는 PKCS#8 PEM 형식의 RSA 개인키다. 줄바꿈을 `\n` 문자로 쓴 한 줄 값도 받는다.
 * 검증용 공개키는 gateway만 가지므로 이 서비스에는 두지 않는다.
 */
@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val privateKey: String,
    val accessTokenTtl: Duration = Duration.ofHours(24),
    val refreshTokenTtl: Duration = Duration.ofDays(7),
) {
    // data class의 기본 toString은 개인키를 그대로 출력한다
    override fun toString() = "JwtProperties(privateKey=***, accessTokenTtl=$accessTokenTtl, refreshTokenTtl=$refreshTokenTtl)"
}
