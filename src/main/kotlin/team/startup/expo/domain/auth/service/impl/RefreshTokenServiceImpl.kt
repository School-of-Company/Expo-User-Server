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
 * 토큰은 `<adminId>.<난수>` 형식이다. Redis에는 관리자당 정렬 집합(`refresh_tokens:<adminId>`) 하나를 두고, 원문이 아니라
 * SHA-256 해시를 멤버로, 만료 시각(초)을 점수로 저장한다. 현장에서 한 계정을 여러 기기가 함께 쓰므로 기기마다 토큰이 하나씩
 * 있고, 한 기기의 로그인이나 재발급은 다른 기기의 토큰에 영향을 주지 않는다. 소유자를 토큰에서 바로 알 수 있어 별도 색인이
 * 필요 없고, 확인과 교체는 키 하나에 대한 단일 스크립트라 [revoke]와 겹쳐도 폐기된 토큰이 되살아나지 않는다.
 *
 * 이전 방식(관리자당 문자열 하나, `refresh_token:<adminId>`)의 토큰은 이 방식에서 찾지 않으므로 배포 직후 한 번은 다시
 * 로그인해야 한다. 이전 키는 만료 시간(TTL)이 지나면 저절로 사라진다.
 */
@Service
class RefreshTokenServiceImpl(
    private val redis: StringRedisTemplate,
    private val properties: JwtProperties,
) : RefreshTokenService {
    private val random = SecureRandom()
    private val issueScript = DefaultRedisScript(ISSUE_SCRIPT, Long::class.java)
    private val rotateScript = DefaultRedisScript(ROTATE_SCRIPT, Long::class.java)

    override fun issue(adminId: Long): IssuedToken {
        val rawToken = newRawToken(adminId)
        val expiresAt = expiresAt()
        redis.execute(
            issueScript,
            listOf(key(adminId)),
            hash(rawToken),
            epochSeconds(expiresAt).toString(),
            nowSeconds().toString(),
            properties.refreshTokenTtl.seconds.toString(),
            properties.maxRefreshTokens.toString(),
        )
        return IssuedToken(rawToken, expiresAt)
    }

    override fun findAdminId(rawToken: String): Long? {
        val adminId = parseAdminId(rawToken) ?: return null
        val expiresAt = redis.opsForZSet().score(key(adminId), hash(rawToken)) ?: return null
        return adminId.takeIf { expiresAt > nowSeconds() }
    }

    override fun rotate(rawToken: String): RotatedRefreshToken? {
        val adminId = parseAdminId(rawToken) ?: return null
        val newRawToken = newRawToken(adminId)
        val expiresAt = expiresAt()
        val rotated =
            redis.execute(
                rotateScript,
                listOf(key(adminId)),
                hash(rawToken),
                hash(newRawToken),
                epochSeconds(expiresAt).toString(),
                nowSeconds().toString(),
                properties.refreshTokenTtl.seconds.toString(),
                properties.maxRefreshTokens.toString(),
            )
        return if (rotated == ROTATED) RotatedRefreshToken(adminId, IssuedToken(newRawToken, expiresAt)) else null
    }

    override fun revoke(adminId: Long) {
        redis.delete(key(adminId))
    }

    override fun revokeToken(
        adminId: Long,
        rawToken: String,
    ) {
        if (parseAdminId(rawToken) != adminId) return
        redis.opsForZSet().remove(key(adminId), hash(rawToken))
    }

    private fun key(adminId: Long) = "$KEY_PREFIX$adminId"

    private fun expiresAt(): LocalDateTime = LocalDateTime.now(ZoneOffset.UTC).plus(properties.refreshTokenTtl)

    private fun epochSeconds(time: LocalDateTime): Long = time.toEpochSecond(ZoneOffset.UTC)

    private fun nowSeconds(): Long = epochSeconds(LocalDateTime.now(ZoneOffset.UTC))

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
        const val KEY_PREFIX = "refresh_tokens:"
        const val TOKEN_BYTES = 32
        const val ROTATED = 1L

        // 만료된 것을 먼저 비우고 새 해시를 더한 뒤, 한도를 넘으면 가장 오래된(점수가 낮은) 것부터 지운다
        const val ISSUE_SCRIPT = """
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[3])
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])
            local over = redis.call('ZCARD', KEYS[1]) - tonumber(ARGV[5])
            if over > 0 then redis.call('ZREMRANGEBYRANK', KEYS[1], 0, over - 1) end
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[4]))
            return 1
        """

        // 저장된 해시일 때만 그 하나를 새 해시로 바꾼다. 키가 없으면(폐기됨) 아무것도 만들지 않고 실패한다.
        const val ROTATE_SCRIPT = """
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[4])
            if redis.call('ZREM', KEYS[1], ARGV[1]) == 0 then
                return 0
            end
            redis.call('ZADD', KEYS[1], ARGV[3], ARGV[2])
            local over = redis.call('ZCARD', KEYS[1]) - tonumber(ARGV[6])
            if over > 0 then redis.call('ZREMRANGEBYRANK', KEYS[1], 0, over - 1) end
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[5]))
            return 1
        """
    }
}
