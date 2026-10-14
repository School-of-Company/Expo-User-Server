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
    fun `같은 관리자가 여러 기기에서 로그인해도 기기마다 토큰이 따로 유효하다`() {
        val first = refreshTokenService.issue(105L)
        val second = refreshTokenService.issue(105L)

        refreshTokenService.findAdminId(first.value) shouldBe 105L
        refreshTokenService.findAdminId(second.value) shouldBe 105L
        (first.value == second.value) shouldBe false
    }

    @Test
    fun `한 기기가 토큰을 교체해도 다른 기기의 토큰은 그대로이다`() {
        val phone = refreshTokenService.issue(111L)
        val tablet = refreshTokenService.issue(111L)

        val rotated = refreshTokenService.rotate(phone.value)!!

        refreshTokenService.findAdminId(phone.value) shouldBe null
        refreshTokenService.findAdminId(rotated.token.value) shouldBe 111L
        refreshTokenService.findAdminId(tablet.value) shouldBe 111L
    }

    @Test
    fun `한 토큰만 폐기하면 그 기기만 로그아웃되고 다른 기기는 유지된다`() {
        val phone = refreshTokenService.issue(112L)
        val tablet = refreshTokenService.issue(112L)

        refreshTokenService.revokeToken(112L, phone.value)

        refreshTokenService.findAdminId(phone.value) shouldBe null
        refreshTokenService.findAdminId(tablet.value) shouldBe 112L
    }

    @Test
    fun `다른 관리자의 토큰이나 없는 토큰으로는 폐기되지 않는다`() {
        val mine = refreshTokenService.issue(113L)
        val other = refreshTokenService.issue(114L)

        refreshTokenService.revokeToken(113L, other.value)
        refreshTokenService.revokeToken(113L, "113.never-issued")
        refreshTokenService.revokeToken(113L, "garbage")

        refreshTokenService.findAdminId(mine.value) shouldBe 113L
        refreshTokenService.findAdminId(other.value) shouldBe 114L
    }

    @Test
    fun `한 관리자가 가질 수 있는 기기 수를 넘으면 가장 오래된 토큰부터 무효가 된다`() {
        val tokens = (1..MAX_TOKENS + 2).map { refreshTokenService.issue(115L) }

        redis.opsForZSet().zCard("refresh_tokens:115") shouldBe MAX_TOKENS.toLong()
        tokens.take(2).forEach { refreshTokenService.findAdminId(it.value) shouldBe null }
        tokens.drop(2).forEach { refreshTokenService.findAdminId(it.value) shouldBe 115L }
    }

    @Test
    fun `교체해도 기기 수 한도를 지킨다`() {
        val tokens = (1..MAX_TOKENS).map { refreshTokenService.issue(116L) }

        val rotated = refreshTokenService.rotate(tokens.last().value)!!

        redis.opsForZSet().zCard("refresh_tokens:116") shouldBe MAX_TOKENS.toLong()
        refreshTokenService.findAdminId(rotated.token.value) shouldBe 116L
    }

    @Test
    fun `만료 시각이 지난 토큰은 저장돼 있어도 찾을 수 없고 교체할 수 없다`() {
        val issued = refreshTokenService.issue(117L)
        val hash = redis.opsForZSet().range("refresh_tokens:117", 0, -1)!!.single()
        redis.opsForZSet().add("refresh_tokens:117", hash, 1.0)

        refreshTokenService.findAdminId(issued.value) shouldBe null
        refreshTokenService.rotate(issued.value) shouldBe null
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

        redis.hasKey("refresh_tokens:108") shouldBe false
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
                redis.hasKey("refresh_tokens:$adminId") shouldBe false
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `원문은 저장하지 않고 만료 시간을 설정한다`() {
        val issued = refreshTokenService.issue(110L)

        val stored = redis.opsForZSet().range("refresh_tokens:110", 0, -1)!!.single()
        stored.contains(issued.value) shouldBe false
        (stored == issued.value) shouldBe false
        val ttl = redis.getExpire("refresh_tokens:110")
        (ttl in 1..Duration.ofDays(7).seconds) shouldBe true
    }

    private companion object {
        // application.yaml의 jwt.max-refresh-tokens 기본값
        const val MAX_TOKENS = 10
    }
}
