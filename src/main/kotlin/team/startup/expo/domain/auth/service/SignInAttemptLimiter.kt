package team.startup.expo.domain.auth.service

import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.security.SignInProperties
import java.security.MessageDigest

/**
 * 닉네임별 로그인 시도 횟수를 Redis에 세어 무차별 대입을 막는다. 비밀번호를 비교하기 전에 시도 한 번을 먼저 차감하고, 한도를 넘으면
 * 비교 없이 거절한다. 증가와 한도 판정이 하나의 스크립트라 동시 요청이 몰려도 한도만큼만 비밀번호 비교까지 간다. 성공하면 [clear]로 되돌리고
 * 실패는 이미 차감되어 있다.
 *
 * 첫 시도에 제한 시간(TTL)을 걸고 그 뒤 시도는 시간을 늘리지 않아, 계속 시도해도 제한이 영원히 이어지지 않는다. TTL이 없는 키가 있으면
 * 다음 시도에 TTL을 건다. 키에는 닉네임 원문이 아니라 해시를 쓴다.
 *
 * Redis에 닿지 못하면 시도를 셀 수 없으므로 로그인을 받지 않고 `503`으로 막는다(fail-closed).
 */
@Component
class SignInAttemptLimiter(
    private val redis: StringRedisTemplate,
    private val properties: SignInProperties,
) {
    private val acquireScript = DefaultRedisScript(ACQUIRE_SCRIPT, Long::class.java)

    /** 시도 한 번을 차감한다. 한도를 넘었으면 남은 잠금 시간과 함께 `429`로 막는다. 비밀번호를 확인하기 전에 호출한다. */
    fun acquire(nickname: String) {
        val remaining =
            redisCall {
                redis.execute(
                    acquireScript,
                    listOf(key(nickname)),
                    properties.lockDuration.seconds.toString(),
                    properties.maxFailures.toString(),
                )
            }
        if (remaining > 0) {
            throw ExpectedException(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.", remaining)
        }
    }

    /** 비밀번호가 맞으면 차감한 시도를 지운다. */
    fun clear(nickname: String) {
        redisCall { redis.delete(key(nickname)) }
    }

    private fun <T> redisCall(action: () -> T): T =
        try {
            action()
        } catch (e: DataAccessException) {
            throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "로그인을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.")
        }

    private fun key(nickname: String): String =
        KEY_PREFIX +
            MessageDigest
                .getInstance("SHA-256")
                .digest(nickname.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

    private companion object {
        const val KEY_PREFIX = "signin_failures:"

        // 허용이면 0, 한도를 넘었으면 남은 잠금 시간(최소 1초)을 돌려준다
        const val ACQUIRE_SCRIPT =
            "local count = redis.call('INCR', KEYS[1]) " +
                "if count == 1 or redis.call('TTL', KEYS[1]) < 0 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end " +
                "if count > tonumber(ARGV[2]) then return math.max(redis.call('TTL', KEYS[1]), 1) end " +
                "return 0"
    }
}
