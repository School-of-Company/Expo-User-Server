package team.startup.expo.global.security.jwt

import io.jsonwebtoken.Jwts
import org.springframework.stereotype.Component
import team.startup.expo.domain.user.entity.Authority
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64
import java.util.Date

/**
 * access token만 JWT(RS256)로 발급한다. 검증은 gateway가 공개키로 하고, 이 서비스는 서명만 한다.
 * `sub`는 `Admin.id`, `role`은 발급 시점의 권한이다.
 */
@Component
class JwtProvider(
    private val properties: JwtProperties,
) {
    private val privateKey: PrivateKey = parsePrivateKey(properties.privateKey)

    fun generateAccessToken(
        adminId: Long,
        authority: Authority,
    ): IssuedToken {
        val issuedAt = Instant.now()
        val expiresAt = issuedAt.plus(properties.accessTokenTtl)
        val token =
            Jwts
                .builder()
                .subject(adminId.toString())
                .claim(ROLE_CLAIM, authority.name)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact()
        return IssuedToken(token, LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault()))
    }

    private fun parsePrivateKey(pem: String): PrivateKey =
        try {
            val body =
                pem
                    .replace("\\n", "\n")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace(Regex("\\s"), "")
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)))
        } catch (e: Exception) {
            // 키 내용이 로그에 남지 않도록 원인 예외는 붙이지 않는다
            throw IllegalStateException("jwt.private-key must be a PKCS#8 PEM encoded RSA private key")
        }

    companion object {
        const val ROLE_CLAIM = "role"
    }
}
