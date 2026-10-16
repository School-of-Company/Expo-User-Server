package team.startup.expo.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.http.HttpStatus
import team.startup.expo.domain.auth.service.SignInAttemptLimiter
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.security.SignInProperties

class SignInAttemptLimiterTests :
    BehaviorSpec({
        given("Redis에 닿지 못할 때") {
            val redis = mockk<StringRedisTemplate>()
            val limiter = SignInAttemptLimiter(redis, SignInProperties())
            every { redis.execute(any<RedisScript<Long>>(), any<List<String>>(), *anyVararg()) } throws
                RedisConnectionFailureException("down")
            every { redis.delete(any<String>()) } throws RedisConnectionFailureException("down")

            `when`("시도를 차감하면") {
                then("시도를 셀 수 없으니 로그인을 받지 않고 503이다") {
                    shouldThrow<ExpectedException> { limiter.acquire("hong") }.status shouldBe HttpStatus.SERVICE_UNAVAILABLE
                }
            }
            `when`("시도를 지우면") {
                then("503이다") {
                    shouldThrow<ExpectedException> { limiter.clear("hong") }.status shouldBe HttpStatus.SERVICE_UNAVAILABLE
                }
            }
        }
    })
