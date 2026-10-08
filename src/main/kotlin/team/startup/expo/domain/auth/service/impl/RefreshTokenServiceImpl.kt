package team.startup.expo.domain.auth.service.impl

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import team.startup.expo.domain.auth.service.RefreshTokenService
import team.startup.expo.domain.auth.service.RotatedRefreshToken
import team.startup.expo.global.security.jwt.IssuedToken
import team.startup.expo.global.security.jwt.JwtProperties
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64

/**
 * refresh token은 JWT가 아니라 불투명한 난수다. gateway는 하나의 공개키로 `sub`만 확인하므로,
 * refresh token을 JWT로 만들면 gateway가 access token으로 받아들이게 된다.
 *
 * 토큰은 `<adminId>.<난수>` 형식이고 Redis에는 관리자당 해시 하나(`refresh_token:<adminId>`)만 둔다.
 * 소유자를 토큰에서 바로 알 수 있어 별도 색인이 필요 없고, 확인과 교체를 키 하나에 대한 단일
 * 스크립트로 처리할 수 있다. 원문은 저장하지 않고 SHA-256 해시만 저장한다.
 * 관리자당 키 하나이므로 단일 기기 로그인 정책이다([RefreshTokenService.issue] 참고).
 */
@Service
class RefreshTokenServiceImpl(
    private val redis: StringRedisTemplate,
    private val properties: JwtProperties,
) : RefreshTokenService {
    private val random = SecureRandom()
    private val rotateScript = DefaultRedisScript(ROTATE_SCRIPT, Long::class.java)

    override fun issue(adminId: Long): IssuedToken {
        val rawToken = newRawToken(adminId)
        redis.opsForValue().set(key(adminId), hash(rawToken), properties.refreshTokenTtl)
        return IssuedToken(rawToken, expiresAt())
    }

    override fun findAdminId(rawToken: String): Long? {
        val adminId = parseAdminId(rawToken) ?: return null
        return adminId.takeIf { redis.opsForValue().get(key(it)) == hash(rawToken) }
    }

    override fun rotate(rawToken: String): RotatedRefreshToken? {
        val adminId = parseAdminId(rawToken) ?: return null
        val newRawToken = newRawToken(adminId)
        val rotated =
            redis.execute(
                rotateScript,
                listOf(key(adminId)),
                hash(rawToken),
                hash(newRawToken),
                properties.refreshTokenTtl.seconds.toString(),
            )
        return if (rotated == ROTATED) RotatedRefreshToken(adminId, IssuedToken(newRawToken, expiresAt())) else null
    }

    override fun revoke(adminId: Long) {
        redis.delete(key(adminId))
    }

    private fun key(adminId: Long) = "$KEY_PREFIX$adminId"

    private fun expiresAt(): LocalDateTime = LocalDateTime.now(ZoneOffset.UTC).plus(properties.refreshTokenTtl)

    private fun newRawToken(adminId: Long): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        return "$adminId.${Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)}"
    }

    private fun parseAdminId(rawToken: String): Long? {
        val adminId = rawToken.substringBefore('.', "").toLongOrNull()
        return adminId?.takeIf { it > 0 && rawToken.length > rawToken.indexOf('.') + 1 }
    }

    private fun hash(rawToken: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(rawToken.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val KEY_PREFIX = "refresh_token:"
        const val TOKEN_BYTES = 32
        const val ROTATED = 1L

        // 저장된 해시가 기대한 값과 같을 때만 새 해시로 교체한다. 키가 없으면(폐기됨) 교체하지 않는다.
        const val ROTATE_SCRIPT = """
            local current = redis.call('GET', KEYS[1])
            if current and current == ARGV[1] then
                redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
                return 1
            end
            return 0
        """
    }
}
