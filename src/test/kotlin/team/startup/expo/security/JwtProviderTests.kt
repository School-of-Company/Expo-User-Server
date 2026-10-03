package team.startup.expo.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.SignatureException
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import team.startup.expo.domain.user.entity.Authority
import team.startup.expo.global.security.jwt.JwtProperties
import team.startup.expo.global.security.jwt.JwtProvider
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.time.Duration
import java.time.LocalDateTime
import java.util.Base64

class JwtProviderTests {
    private val keyPair = newKeyPair()

    @Test
    fun `access token은 RS256으로 서명되고 sub와 role을 담는다`() {
        val provider = JwtProvider(JwtProperties(privateKey = pem(keyPair)))

        val issued = provider.generateAccessToken(7L, Authority.ROLE_ADMIN)

        val jws =
            Jwts
                .parser()
                .verifyWith(keyPair.public)
                .build()
                .parseSignedClaims(issued.value)
        jws.header["alg"] shouldBe "RS256"
        jws.payload.subject shouldBe "7"
        jws.payload["role"] shouldBe "ROLE_ADMIN"
    }

    @Test
    fun `만료 시각은 설정한 TTL을 따른다`() {
        val provider = JwtProvider(JwtProperties(privateKey = pem(keyPair), accessTokenTtl = Duration.ofMinutes(30)))
        val before = LocalDateTime.now()

        val issued = provider.generateAccessToken(1L, Authority.ROLE_ADMIN)

        val jws =
            Jwts
                .parser()
                .verifyWith(keyPair.public)
                .build()
                .parseSignedClaims(issued.value)
        val ttlMillis = jws.payload.expiration.time - jws.payload.issuedAt.time
        // JWT의 exp/iat는 초 단위로 잘리므로 1초의 오차를 허용한다
        (ttlMillis in (Duration.ofMinutes(30).toMillis() - 1000)..(Duration.ofMinutes(30).toMillis() + 1000)) shouldBe true
        issued.expiresAt.isAfter(before.plusMinutes(29)) shouldBe true
        issued.expiresAt.isBefore(before.plusMinutes(31)) shouldBe true
    }

    @Test
    fun `다른 공개키로는 검증되지 않는다`() {
        val provider = JwtProvider(JwtProperties(privateKey = pem(keyPair)))
        val token = provider.generateAccessToken(7L, Authority.ROLE_ADMIN).value

        assertThrows(SignatureException::class.java) {
            Jwts
                .parser()
                .verifyWith(newKeyPair().public)
                .build()
                .parseSignedClaims(token)
        }
    }

    @Test
    fun `줄바꿈을 이스케이프한 한 줄 PEM도 받는다`() {
        val oneLine = pem(keyPair).replace("\n", "\\n")
        val provider = JwtProvider(JwtProperties(privateKey = oneLine))

        val token = provider.generateAccessToken(3L, Authority.ROLE_ADMIN).value

        Jwts
            .parser()
            .verifyWith(keyPair.public)
            .build()
            .parseSignedClaims(token)
            .payload.subject shouldBe "3"
    }

    @Test
    fun `잘못된 키는 기동 시점에 실패하고 키 내용을 노출하지 않는다`() {
        val secret = "not-a-pem-SECRET-VALUE"

        val exception = assertThrows(IllegalStateException::class.java) { JwtProvider(JwtProperties(privateKey = secret)) }

        (exception.message?.contains(secret) == true) shouldBe false
        exception.cause shouldBe null
    }

    private fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun pem(pair: KeyPair): String {
        val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(pair.private.encoded)
        return "-----BEGIN PRIVATE KEY-----\n$body\n-----END PRIVATE KEY-----"
    }
}
