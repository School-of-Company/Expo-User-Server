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
    fun `저장하지 않은 토큰은 찾을 수 없다`() {
        refreshTokenService.issue(102L)

        refreshTokenService.findAdminId("never-issued-token") shouldBe null
    }

    @Test
    fun `다시 발급하면 이전 토큰은 무효가 된다`() {
        val first = refreshTokenService.issue(103L)
        val second = refreshTokenService.issue(103L)

        refreshTokenService.findAdminId(first.value) shouldBe null
        refreshTokenService.findAdminId(second.value) shouldBe 103L
    }

    @Test
    fun `폐기하면 찾을 수 없다`() {
        val issued = refreshTokenService.issue(104L)

        refreshTokenService.revoke(104L)

        refreshTokenService.findAdminId(issued.value) shouldBe null
    }

    @Test
    fun `원문은 저장하지 않고 만료 시간을 설정한다`() {
        val issued = refreshTokenService.issue(105L)

        val stored = redis.opsForHash<String, String>().entries("refresh_token:105")
        stored.values.none { it.contains(issued.value) } shouldBe true
        val ttl = redis.getExpire("refresh_token:105") ?: -1L
        (ttl in 1..Duration.ofDays(7).seconds) shouldBe true
    }
}
