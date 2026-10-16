package team.startup.expo.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import team.startup.expo.domain.auth.service.impl.ParticipantSessionServiceImpl
import team.startup.expo.domain.auth.service.impl.StoredSession
import team.startup.expo.global.security.ParticipantSessionProperties
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class ParticipantSessionClockTests :
    BehaviorSpec({
        given("TTL 1초 세션을 x.999초에 발급했을 때") {
            val redis = mockk<StringRedisTemplate>()
            val jsonMapper = mockk<JsonMapper>()
            var stored: StoredSession? = null
            every { jsonMapper.writeValueAsString(any<StoredSession>()) } answers {
                stored = firstArg()
                "json"
            }
            every { jsonMapper.readValue("json", StoredSession::class.java) } answers { stored!! }
            every { redis.execute(any<RedisScript<Long>>(), any<List<String>>(), *anyVararg()) } returns 1L
            every { redis.opsForValue().get(any()) } returns "json"

            val properties = ParticipantSessionProperties(ttl = Duration.ofSeconds(1))

            fun service(at: String) =
                ParticipantSessionServiceImpl(redis, properties, jsonMapper, Clock.fixed(Instant.parse(at), ZoneOffset.UTC))

            val issued = service("2026-10-16T00:00:10.999Z").issueIfCodeUnused(EXPO, PHONE, "123456").shouldNotBeNull()

            `when`("만료 시각과 같은 초 안에서 확인하면") {
                then("아직 유효하다") {
                    service("2026-10-16T00:00:11.500Z").find(issued.sessionId).shouldNotBeNull()
                }
            }
            `when`("정확한 만료 시각에 확인하면") {
                then("만료다") {
                    service("2026-10-16T00:00:11.999Z").find(issued.sessionId).shouldBeNull()
                }
            }
        }
    }) {
    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-0000000000e3"
        const val PHONE = "01012345679"
    }
}
