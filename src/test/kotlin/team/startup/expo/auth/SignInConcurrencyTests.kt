package team.startup.expo.auth

import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import team.startup.expo.domain.auth.presentation.dto.request.SignInReqDto
import team.startup.expo.domain.auth.service.SignInService
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.support.IntegrationTestSupport
import java.time.Duration
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SignInConcurrencyTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var signInService: SignInService

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var redis: StringRedisTemplate

    @MockitoSpyBean
    private lateinit var passwordEncoder: PasswordEncoder

    @BeforeEach
    fun setUp() {
        clearAdmins()
        clearAttempts()
        adminRepository.save(
            Admin(
                name = "관리자",
                nickname = NICKNAME,
                email = "accepted@gsm.hs.kr",
                password = requireNotNull(passwordEncoder.encode(PASSWORD)),
                phoneNumber = "01011110001",
            ).apply { accept() },
        )
    }

    @Test
    fun `한도 직전에 실패 요청 10개가 동시에 들어와도 비밀번호 비교는 1번이고 나머지는 429다`() {
        failUntilOneAttemptLeft()

        val results = concurrently(List(10) { WRONG_PASSWORD })

        results shouldContainExactlyInAnyOrder listOf(HttpStatus.BAD_REQUEST) + List(9) { HttpStatus.TOO_MANY_REQUESTS }
        verify(passwordEncoder, times(1)).matches(anyString(), anyString())
    }

    @Test
    fun `한도 직전에 맞는 비밀번호와 틀린 비밀번호가 경합해도 한도를 넘어 비교하지 않는다`() {
        repeat(RACE_ROUNDS) {
            clearAttempts()
            failUntilOneAttemptLeft()

            val (correct, wrong) = concurrently(listOf(PASSWORD, WRONG_PASSWORD))

            // 남은 한 번을 먼저 차감한 쪽만 비교한다. 성공이 먼저 끝나 횟수를 지운 뒤 들어온 실패만 새로 비교할 수 있다
            listOf(correct, wrong) shouldBeIn
                listOf(
                    listOf(HttpStatus.OK, HttpStatus.TOO_MANY_REQUESTS),
                    listOf(HttpStatus.TOO_MANY_REQUESTS, HttpStatus.BAD_REQUEST),
                    listOf(HttpStatus.OK, HttpStatus.BAD_REQUEST),
                )
            // 틀린 비밀번호가 남은 한 번을 썼으면 잠긴 채로 남는다
            if (correct == HttpStatus.TOO_MANY_REQUESTS) signIn(PASSWORD) shouldBe HttpStatus.TOO_MANY_REQUESTS
        }
    }

    @Test
    fun `잠금 시간이 지나면 다시 로그인할 수 있다`() {
        repeat(MAX_FAILURES) { signIn(WRONG_PASSWORD) shouldBe HttpStatus.BAD_REQUEST }
        signIn(PASSWORD) shouldBe HttpStatus.TOO_MANY_REQUESTS

        val key = attemptKey()
        redis.expire(key, Duration.ofMillis(100))
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (redis.hasKey(key) && System.nanoTime() < deadline) Thread.sleep(20)

        signIn(PASSWORD) shouldBe HttpStatus.OK
    }

    @Test
    fun `만료 시간이 없는 시도 키가 있어도 다음 시도에 만료 시간을 걸어 영구히 잠기지 않는다`() {
        signIn(WRONG_PASSWORD) shouldBe HttpStatus.BAD_REQUEST
        redis.persist(attemptKey())

        signIn(WRONG_PASSWORD) shouldBe HttpStatus.BAD_REQUEST

        (redis.getExpire(attemptKey()) > 0) shouldBe true
    }

    private fun failUntilOneAttemptLeft() {
        repeat(MAX_FAILURES - 1) { signIn(WRONG_PASSWORD) shouldBe HttpStatus.BAD_REQUEST }
        clearInvocations(passwordEncoder)
    }

    /** 모든 요청을 barrier에서 한꺼번에 출발시키고 요청 순서대로 결과를 돌려준다. */
    private fun concurrently(passwords: List<String>): List<HttpStatus> {
        val barrier = CyclicBarrier(passwords.size)
        val executor = Executors.newFixedThreadPool(passwords.size)
        try {
            return passwords
                .map { password ->
                    executor.submit<HttpStatus> {
                        barrier.await()
                        signIn(password)
                    }
                }.map { it.get(10, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun signIn(password: String): HttpStatus =
        try {
            signInService.execute(SignInReqDto(NICKNAME, password))
            HttpStatus.OK
        } catch (e: ExpectedException) {
            e.status
        }

    private fun attemptKey(): String = redis.keys("signin_failures:*").single()

    private fun clearAttempts() {
        redis.keys("signin_failures:*").takeIf { it.isNotEmpty() }?.let(redis::delete)
    }

    private companion object {
        const val NICKNAME = "accepted"
        const val PASSWORD = "Passw0rd!"
        const val WRONG_PASSWORD = "Wrong1234!"
        const val MAX_FAILURES = 5
        const val RACE_ROUNDS = 10
    }
}
