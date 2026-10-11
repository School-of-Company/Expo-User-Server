package team.startup.expo.auth

import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.support.FakeExpoServer
import team.startup.expo.support.FakeNotificationServer
import team.startup.expo.support.IntegrationTestSupport

/** 세션 유효 시간이 지나면 세션 만료로 답한다. 시간을 줄인 별도 컨텍스트로 확인한다. */
@TestPropertySource(properties = ["participant-session.ttl=1s"])
class ParticipantSessionExpiryTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var redis: StringRedisTemplate

    @BeforeEach
    fun setUp() {
        FakeExpoServer.reset()
        FakeNotificationServer.reset()
        FakeExpoServer.register(EXPO, "2026-10-01", "2026-10-31")
        redis.keys("participant_*").takeIf { it.isNotEmpty() }?.let(redis::delete)
    }

    @Test
    fun `유효 시간이 지나면 세션 확인과 내부 조회가 SESSION_EXPIRED이다`() {
        post("/participant-auth/code", body()).andExpect(status().isOk)
        val verified = post("/participant-auth/verify", body(FakeNotificationServer.VALID_CODE)).andExpect(status().isOk).andReturn()
        val sessionId =
            verified.response
                .getHeader(HttpHeaders.SET_COOKIE)!!
                .substringBefore(';')
                .substringAfter("$COOKIE=")
        check(sessionId)
            .andExpect(status().isOk)

        Thread.sleep(WAIT_MILLIS)

        check(sessionId)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"))
        mockMvc
            .perform(
                post("/internal/participant-sessions/resolve")
                    .header("X-Internal-Token", INTERNAL_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"sessionId":"$sessionId","expoId":"$EXPO"}"""),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"))
    }

    private fun post(
        path: String,
        content: String,
    ) = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(content))

    private fun check(sessionId: String) =
        mockMvc.perform(get("/participant-auth/session").param("expoId", EXPO).cookie(Cookie(COOKIE, sessionId)))

    private fun body(code: String? = null) =
        if (code == null) {
            """{"expoId":"$EXPO","phoneNumber":"$PHONE"}"""
        } else {
            """{"expoId":"$EXPO","phoneNumber":"$PHONE","code":"$code"}"""
        }

    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-0000000000e3"
        const val PHONE = "01012345679"
        const val COOKIE = "PARTICIPANT_SESSION"
        const val WAIT_MILLIS = 1_500L
    }
}
