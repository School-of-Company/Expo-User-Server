package team.startup.expo.global.security.jwt

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.SignatureException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import team.startup.expo.domain.user.entity.Authority
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.Date

/**
 * access token을 JWT(RS256)로 발급하고, 받은 토큰이 우리가 서명한 것인지 검증한다.
 * `sub`는 `Admin.id`, `role`은 발급 시점의 권한이다.
 *
 * gateway도 공개키로 같은 검증을 하지만 이 서비스는 gateway를 믿지 않고 직접 검증한다. 다른 서비스가
 * 이 서비스에 직접 접속할 수 있으므로, `X-User-Id` 같은 헤더만으로 관리자를 인증하면 직접 접속한
 * 쪽이 관리자 id를 넣어 일반 경로를 호출할 수 있기 때문이다. 우리가 서명한 토큰이 없으면 만들 수 없다.
 * 공개키는 개인키에서 구하므로 따로 설정하지 않는다.
 */
@Component
class JwtProvider(
    private val properties: JwtProperties,
) {
    private val privateKey: PrivateKey = parsePrivateKey(properties.privateKey)
    private val publicKey: PublicKey = derivePublicKey(privateKey)
    private val log = LoggerFactory.getLogger(javaClass)

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
        return IssuedToken(token, LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC))
    }

    /** 서명이 맞고 만료되지 않았으며 RS256으로 서명된 토큰이면 `sub`의 관리자 id를, 아니면 null을 돌려준다. */
    fun verifyAdminId(token: String): Long? =
        try {
            val jws =
                Jwts
                    .parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
            jws.payload.subject
                ?.toLongOrNull()
                ?.takeIf { it > 0 && jws.header.algorithm == ALGORITHM }
        } catch (e: SignatureException) {
            // 키 교체 후 서명이 맞지 않는 경우를 운영에서 알아볼 수 있게 한다. 토큰 내용은 남기지 않는다
            log.warn("access token signature verification failed")
            null
        } catch (e: JwtException) {
            // 만료와 형식 오류는 흔하므로 조용히 거절한다
            null
        } catch (e: IllegalArgumentException) {
            null
        }

    private fun derivePublicKey(key: PrivateKey): PublicKey {
        val crtKey =
            key as? RSAPrivateCrtKey
                ?: throw IllegalStateException("jwt.private-key must be an RSA private key")
        return KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(crtKey.modulus, crtKey.publicExponent))
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
        private const val ALGORITHM = "RS256"
    }
}
