package team.startup.expo.security

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import team.startup.expo.domain.auth.service.RefreshTokenService
import team.startup.expo.support.IntegrationTestSupport
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RefreshTokenServiceTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var refreshTokenService: RefreshTokenService

    @Autowired
    private lateinit var redis: StringRedisTemplate

    @Test
    fun `발급한 토큰으로 소유자를 찾을 수 있다`() {
        val issued = refreshTokenService.issue(101L)

        refreshTokenService.findAdminId(issued.value) shouldBe 101L
        issued.expiresAt.isAfter(LocalDateTime.now(ZoneOffset.UTC).plusDays(6)) shouldBe true
        issued.expiresAt.isBefore(LocalDateTime.now(ZoneOffset.UTC).plusDays(8)) shouldBe true
    }

    @Test
    fun `저장하지 않은 토큰이나 형식이 틀린 토큰은 찾을 수 없다`() {
        refreshTokenService.issue(102L)

        refreshTokenService.findAdminId("never-issued-token") shouldBe null
        refreshTokenService.findAdminId("102.never-issued") shouldBe null
        refreshTokenService.findAdminId("102.") shouldBe null
        refreshTokenService.findAdminId("abc.def") shouldBe null
        refreshTokenService.findAdminId("-1.def") shouldBe null
    }

    @Test
    fun `다른 관리자 id를 붙여 만든 토큰은 통하지 않는다`() {
        val issued = refreshTokenService.issue(103L)
        val forged = "104." + issued.value.substringAfter('.')
        refreshTokenService.issue(104L)

        refreshTokenService.findAdminId(forged) shouldBe null
        refreshTokenService.rotate(forged) shouldBe null
        refreshTokenService.findAdminId(issued.value) shouldBe 103L
    }

    @Test
    fun `다시 발급하면 이전 토큰은 무효가 된다`() {
        val first = refreshTokenService.issue(105L)
        val second = refreshTokenService.issue(105L)

        refreshTokenService.findAdminId(first.value) shouldBe null
        refreshTokenService.findAdminId(second.value) shouldBe 105L
    }

    @Test
    fun `폐기하면 찾을 수 없다`() {
        val issued = refreshTokenService.issue(106L)

        refreshTokenService.revoke(106L)

        refreshTokenService.findAdminId(issued.value) shouldBe null
    }

    @Test
    fun `교체하면 새 토큰만 유효하고 이전 토큰은 다시 쓸 수 없다`() {
        val issued = refreshTokenService.issue(107L)

        val rotated = refreshTokenService.rotate(issued.value)!!

        rotated.adminId shouldBe 107L
        (rotated.token.value == issued.value) shouldBe false
        refreshTokenService.findAdminId(issued.value) shouldBe null
        refreshTokenService.findAdminId(rotated.token.value) shouldBe 107L
        refreshTokenService.rotate(issued.value) shouldBe null
    }

    @Test
    fun `폐기한 뒤의 교체는 실패하고 되살아나지 않는다`() {
        val issued = refreshTokenService.issue(108L)
        refreshTokenService.revoke(108L)

        refreshTokenService.rotate(issued.value) shouldBe null

        redis.hasKey("refresh_token:108") shouldBe false
    }

    @Test
    fun `교체한 뒤의 폐기는 새 토큰까지 무효로 만든다`() {
        val issued = refreshTokenService.issue(109L)
        val rotated = refreshTokenService.rotate(issued.value)!!

        refreshTokenService.revoke(109L)

        refreshTokenService.findAdminId(rotated.token.value) shouldBe null
    }

    @Test
    fun `교체와 폐기가 동시에 일어나도 폐기가 끝난 뒤에는 유효한 토큰이 남지 않는다`() {
        val executor = Executors.newFixedThreadPool(2)
        try {
            repeat(200) { iteration ->
                val adminId = 1_000L + iteration
                val issued = refreshTokenService.issue(adminId)
                val start = CountDownLatch(1)
                val done = CountDownLatch(2)

                executor.submit {
                    start.await()
                    refreshTokenService.rotate(issued.value)
                    done.countDown()
                }
                executor.submit {
                    start.await()
                    refreshTokenService.revoke(adminId)
                    done.countDown()
                }
                start.countDown()
                done.await(10, TimeUnit.SECONDS) shouldBe true

                // 어느 쪽이 먼저든 폐기가 끝났다면 저장된 토큰은 없어야 한다
                redis.hasKey("refresh_token:$adminId") shouldBe false
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `원문은 저장하지 않고 만료 시간을 설정한다`() {
        val issued = refreshTokenService.issue(110L)

        val stored = redis.opsForValue().get("refresh_token:110")!!
        stored.contains(issued.value) shouldBe false
        (stored == issued.value) shouldBe false
        val ttl = redis.getExpire("refresh_token:110")
        (ttl in 1..Duration.ofDays(7).seconds) shouldBe true
    }
}
