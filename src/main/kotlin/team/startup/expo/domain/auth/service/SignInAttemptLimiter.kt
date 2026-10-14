package team.startup.expo.domain.auth.service

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.security.SignInProperties
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 닉네임별 로그인 실패 횟수를 Redis에 세어 무차별 대입을 막는다. 첫 실패에 제한 시간(TTL)을 걸고 그 뒤 실패는 시간을 늘리지
 * 않아, 계속 시도해도 제한이 영원히 이어지지 않는다. 횟수 증가와 TTL 설정은 하나의 스크립트라 둘 사이에서 죽어도 TTL 없는 키가 남지
 * 않는다. 키에는 닉네임 원문이 아니라 해시를 쓴다.
 */
@Component
class SignInAttemptLimiter(
    private val redis: StringRedisTemplate,
    private val properties: SignInProperties,
) {
    private val incrementScript = DefaultRedisScript(INCREMENT_SCRIPT, Long::class.java)

    /** 이미 제한 횟수만큼 실패했으면 `429`로 막는다. 비밀번호를 확인하기 전에 호출한다. */
    fun requireNotLocked(nickname: String) {
        val failures = redis.opsForValue().get(key(nickname))?.toIntOrNull() ?: 0
        if (failures >= properties.maxFailures) {
            // TTL이 없는 키(-1)나 방금 만료된 키(-2)여도 헤더가 0 이하가 되지 않게 최소 1초로 맞춘다
            val remaining = (redis.getExpire(key(nickname), TimeUnit.SECONDS)).coerceAtLeast(1L)
            throw ExpectedException(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.", remaining)
        }
    }

    fun recordFailure(nickname: String) {
        redis.execute(incrementScript, listOf(key(nickname)), properties.lockDuration.seconds.toString())
    }

    /** 로그인에 성공하면 실패 횟수를 지운다. */
    fun clear(nickname: String) {
        redis.delete(key(nickname))
    }

    private fun key(nickname: String): String =
        KEY_PREFIX +
            MessageDigest
                .getInstance("SHA-256")
                .digest(nickname.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

    private companion object {
        const val KEY_PREFIX = "signin_failures:"
        const val INCREMENT_SCRIPT =
            "local count = redis.call('INCR', KEYS[1]) " +
                "if count == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end " +
                "return count"
    }
}
