package team.startup.expo.domain.auth.service.impl

import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import team.startup.expo.domain.auth.model.IssuedParticipantSession
import team.startup.expo.domain.auth.model.ParticipantSession
import team.startup.expo.domain.auth.service.ParticipantSessionService
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.security.ParticipantSessionProperties
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64

/**
 * 세션은 `participant_session:<해시>` 키에 JSON으로 두고 TTL로 만료시킨다. 키는 불투명 ID의 SHA-256 해시라, Redis를 읽을 수
 * 있어도 쿠키로 쓸 수 있는 원문 ID는 알 수 없다. ID는 `SecureRandom` 32바이트라 추측할 수 없다.
 *
 * Redis에 접근하지 못하면 "세션 없음"으로 돌려보내지 않고 503으로 실패시킨다. 장애를 만료로 처리하면 사용자가 멀쩡한
 * 세션을 두고 다시 인증하게 되고, 인증 직후라면 세션이 만들어지지 않은 채 성공처럼 보이기 때문이다.
 */
@Service
class ParticipantSessionServiceImpl(
    private val redis: StringRedisTemplate,
    private val properties: ParticipantSessionProperties,
    private val jsonMapper: JsonMapper,
) : ParticipantSessionService {
    private val random = SecureRandom()

    override fun issue(
        expoId: String,
        phoneNumber: String,
    ): IssuedParticipantSession {
        val sessionId = newSessionId()
        val expiresAt = LocalDateTime.now(ZoneOffset.UTC).plus(properties.ttl)
        val stored = StoredSession(expoId, phoneNumber, expiresAt.toEpochSecond(ZoneOffset.UTC))
        redisCall { redis.opsForValue().set(sessionKey(sessionId), jsonMapper.writeValueAsString(stored), properties.ttl) }
        return IssuedParticipantSession(sessionId, ParticipantSession(expoId, phoneNumber, expiresAt))
    }

    override fun find(sessionId: String): ParticipantSession? {
        val json = redisCall { redis.opsForValue().get(sessionKey(sessionId)) } ?: return null
        val stored =
            try {
                jsonMapper.readValue(json, StoredSession::class.java)
            } catch (e: Exception) {
                return null
            }
        // TTL이 지났어야 할 값이 남아 있는 경우를 대비해 저장된 만료 시각도 확인한다
        if (stored.expiresAtEpochSecond <= Instant.now().epochSecond) return null
        return ParticipantSession(
            expoId = stored.expoId,
            phoneNumber = stored.phoneNumber,
            expiresAt = LocalDateTime.ofEpochSecond(stored.expiresAtEpochSecond, 0, ZoneOffset.UTC),
        )
    }

    override fun revoke(sessionId: String) {
        redisCall { redis.delete(sessionKey(sessionId)) }
    }

    override fun markCodeUsed(
        phoneNumber: String,
        code: String,
    ): Boolean = redisCall { redis.opsForValue().setIfAbsent(usedCodeKey(phoneNumber, code), "1", properties.usedCodeTtl) } == true

    private fun <T> redisCall(action: () -> T): T =
        try {
            action()
        } catch (e: DataAccessException) {
            throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "인증 세션을 처리할 수 없습니다.")
        }

    private fun sessionKey(sessionId: String) = "$SESSION_PREFIX${hash(sessionId)}"

    // 번호와 코드를 함께 해시해 키에 원문이 남지 않게 한다
    private fun usedCodeKey(
        phoneNumber: String,
        code: String,
    ) = "$USED_CODE_PREFIX${hash("$phoneNumber:$code")}"

    private fun newSessionId(): String {
        val bytes = ByteArray(SESSION_ID_BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private companion object {
        const val SESSION_PREFIX = "participant_session:"
        const val USED_CODE_PREFIX = "participant_otp_used:"
        const val SESSION_ID_BYTES = 32
    }
}

/** Redis에 JSON으로 저장하는 세션. 전화번호가 들어 있으므로 로그에 찍지 않는다. */
internal data class StoredSession(
    val expoId: String,
    val phoneNumber: String,
    val expiresAtEpochSecond: Long,
) {
    override fun toString() = "StoredSession(expoId=$expoId, phoneNumber=***, expiresAtEpochSecond=$expiresAtEpochSecond)"
}
