package team.startup.expo.domain.auth.service.impl

import org.springframework.stereotype.Service
import team.startup.expo.domain.auth.entity.RefreshToken
import team.startup.expo.domain.auth.repository.RefreshTokenRepository
import team.startup.expo.domain.auth.service.RefreshTokenService
import team.startup.expo.global.security.jwt.IssuedToken
import team.startup.expo.global.security.jwt.JwtProperties
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64

/**
 * refresh token은 JWT가 아니라 불투명한 난수다. gateway는 하나의 공개키로 `sub`만 확인하므로,
 * refresh token을 JWT로 만들면 gateway가 access token으로 받아들이게 된다.
 */
@Service
class RefreshTokenServiceImpl(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val properties: JwtProperties,
) : RefreshTokenService {
    private val random = SecureRandom()

    override fun issue(adminId: Long): IssuedToken {
        val rawToken = generateRawToken()
        refreshTokenRepository.save(RefreshToken(adminId, hash(rawToken), properties.refreshTokenTtl.seconds))
        return IssuedToken(rawToken, LocalDateTime.now().plus(properties.refreshTokenTtl))
    }

    override fun findAdminId(rawToken: String): Long? = refreshTokenRepository.findByTokenHash(hash(rawToken))?.adminId

    override fun revoke(adminId: Long) {
        refreshTokenRepository.deleteById(adminId)
    }

    private fun generateRawToken(): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hash(rawToken: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(rawToken.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val TOKEN_BYTES = 32
    }
}
