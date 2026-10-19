package team.startup.expo.auth

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.auth.service.ParticipantSessionService
import team.startup.expo.support.FakeExpoServer
import team.startup.expo.support.FakeNotificationServer
import team.startup.expo.support.IntegrationTestSupport
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ParticipantSessionTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var redis: StringRedisTemplate

    @Autowired
    private lateinit var participantSessionService: ParticipantSessionService

    private val executor = Executors.newFixedThreadPool(THREADS)

    private fun verifiedCount() = jdbcTemplate.queryForObject("SELECT count(*) FROM tb_participant_phone_verification", Long::class.java)

    @BeforeEach
    fun setUp() {
        FakeExpoServer.reset()
        FakeNotificationServer.reset()
        FakeExpoServer.register(EXPO, "2026-10-01", "2026-10-31")
        FakeExpoServer.register(OTHER_EXPO, "2026-10-01", "2026-10-31")
        clearSessions()
    }

    @AfterEach
    fun tearDown() {
        executor.shutdownNow()
        clearSessions()
    }

    // --- POST /participant-auth/code

    @Test
    fun `박람회가 있으면 Notification에 번호만 보내 인증번호를 요청한다`() {
        sendCode(EXPO, PHONE).andExpect(status().isOk)

        FakeNotificationServer.sentPhoneNumbers shouldBe listOf(PHONE)
    }

    @Test
    fun `인증번호 요청에 Notification 내부 토큰을 실어 내부 상한으로 세게 한다`() {
        sendCode(EXPO, PHONE).andExpect(status().isOk)

        FakeNotificationServer.sendTokens shouldBe listOf(FakeNotificationServer.TOKEN)
    }

    @Test
    fun `없는 박람회는 404이고 문자를 요청하지 않는다`() {
        sendCode("0199aaaa-0000-7000-8000-00000000ffff", PHONE).andExpect(status().isNotFound)

        FakeNotificationServer.sentPhoneNumbers shouldBe emptyList()
    }

    @Test
    fun `형식이 틀린 번호와 박람회 id는 400이고 문자를 요청하지 않는다`() {
        sendCode(EXPO, "010-1234-5678").andExpect(status().isBadRequest)
        sendCode(EXPO, "12345").andExpect(status().isBadRequest)
        sendCode("", PHONE).andExpect(status().isBadRequest)
        sendCode("x".repeat(37), PHONE).andExpect(status().isBadRequest)

        FakeNotificationServer.sentPhoneNumbers shouldBe emptyList()
    }

    @Test
    fun `Notification의 횟수 제한은 429로, 발송 실패나 장애는 503으로 돌려준다`() {
        FakeNotificationServer.sendStatus = 429
        sendCode(EXPO, PHONE).andExpect(status().isTooManyRequests)

        FakeNotificationServer.sendStatus = 502
        sendCode(EXPO, PHONE).andExpect(status().isServiceUnavailable)
    }

    // --- POST /participant-auth/verify

    @Test
    fun `인증번호가 맞으면 세션을 만들고 HttpOnly 쿠키로 보낸다`() {
        sendCode(EXPO, PHONE).andExpect(status().isOk)

        val result =
            verify(EXPO, PHONE, FakeNotificationServer.VALID_CODE)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.expoId").value(EXPO))
                .andExpect(jsonPath("$.expiresAt").value(org.hamcrest.Matchers.matchesPattern(EXPIRES_AT_PATTERN)))
                .andExpect(jsonPath("$.phoneNumber").doesNotExist())
                .andReturn()

        val setCookie = result.response.getHeader(HttpHeaders.SET_COOKIE)!!
        setCookie shouldContain "$COOKIE=${sessionIdOf(result)}"
        setCookie shouldContain "HttpOnly"
        setCookie shouldContain "Secure"
        setCookie shouldContain "SameSite=Lax"
        setCookie shouldContain "Path=/"
        setCookie shouldContain "Max-Age="
        // 전화번호는 쿠키와 응답 본문에 싣지 않는다
        setCookie shouldNotContain PHONE
        result.response.contentAsString shouldNotContain PHONE
        FakeNotificationServer.verifiedRequests shouldBe listOf(PHONE to FakeNotificationServer.VALID_CODE)
    }

    @Test
    fun `Redis에는 쿠키로 쓸 수 있는 원문 ID를 두지 않고 해시 키로 둔다`() {
        sendCode(EXPO, PHONE)
        val sessionId = sessionIdOf(verify(EXPO, PHONE, FakeNotificationServer.VALID_CODE).andReturn())

        val keys = redis.keys("participant_session:*")

        keys.size shouldBe 1
        keys.single() shouldNotContain sessionId
        val ttl = redis.getExpire(keys.single())!!
        (ttl in 1..SESSION_TTL_SECONDS) shouldBe true
    }

    @Test
    fun `인증번호가 틀리면 400이고 세션도 쿠키도 만들지 않는다`() {
        sendCode(EXPO, PHONE)

        val result = verify(EXPO, PHONE, "9999").andExpect(status().isBadRequest).andReturn()

        result.response.getHeader(HttpHeaders.SET_COOKIE) shouldBe null
        redis.keys("participant_session:*").size shouldBe 0
    }

    @Test
    fun `인증을 요청한 적 없거나 횟수를 넘으면 Notification 상태 그대로 404와 429이다`() {
        verify(EXPO, PHONE, FakeNotificationServer.VALID_CODE).andExpect(status().isNotFound)

        FakeNotificationServer.verifyStatus = 429
        verify(EXPO, PHONE, FakeNotificationServer.VALID_CODE).andExpect(status().isTooManyRequests)

        FakeNotificationServer.verifyStatus = 500
        verify(EXPO, PHONE, FakeNotificationServer.VALID_CODE).andExpect(status().isServiceUnavailable)
        redis.keys("participant_session:*").size shouldBe 0
    }

    @Test
    fun `없는 박람회에는 인증이 맞아도 세션을 만들지 않는다`() {
        sendCode(EXPO, PHONE)

        verify("0199aaaa-0000-7000-8000-00000000ffff", PHONE, FakeNotificationServer.VALID_CODE).andExpect(status().isNotFound)

        redis.keys("participant_session:*").size shouldBe 0
    }

    @Test
    fun `성공한 인증번호를 다시 보내면 409이고 세션을 하나만 만든다`() {
        sendCode(EXPO, PHONE)
        verify(EXPO, PHONE, FakeNotificationServer.VALID_CODE).andExpect(status().isOk)

        verify(EXPO, PHONE, FakeNotificationServer.VALID_CODE).andExpect(status().isConflict)

        redis.keys("participant_session:*").size shouldBe 1
    }

    @Test
    fun `같은 인증번호를 동시에 보내도 세션은 하나만 만든다`() {
        sendCode(EXPO, PHONE)

        val statuses = concurrently { verify(EXPO, PHONE, FakeNotificationServer.VALID_CODE).andReturn().response.status }

        statuses.count { it == 200 } shouldBe 1
        statuses.count { it == 409 } shouldBe THREADS - 1
        redis.keys("participant_session:*").size shouldBe 1
    }

    @Test
    fun `인증번호 사용 기록과 세션은 함께 만들어지고 이미 쓴 코드면 세션을 하나도 만들지 않는다`() {
        val first = participantSessionService.issueIfCodeUnused(EXPO, PHONE, "1234")

        checkNotNull(first)
        redis.keys("participant_otp_used:*").size shouldBe 1
        redis.keys("participant_session:*").size shouldBe 1

        val second = participantSessionService.issueIfCodeUnused(EXPO, PHONE, "1234")

        second shouldBe null
        redis.keys("participant_otp_used:*").size shouldBe 1
        redis.keys("participant_session:*").size shouldBe 1
    }

    @Test
    fun `같은 번호의 다른 인증번호는 각각 세션을 받는다`() {
        val first = participantSessionService.issueIfCodeUnused(EXPO, PHONE, "1234")
        val second = participantSessionService.issueIfCodeUnused(EXPO, PHONE, "5678")

        checkNotNull(first)
        checkNotNull(second)
        (first.sessionId == second.sessionId) shouldBe false
        redis.keys("participant_session:*").size shouldBe 2
    }

    @Test
    fun `인증에 성공한 번호만 박람회별로 한 번 해시로 기록하고 번호 원문은 저장하지 않는다`() {
        jdbcTemplate.execute("DELETE FROM tb_participant_phone_verification")
        sendCode(EXPO, PHONE).andExpect(status().isOk)

        // 틀린 인증번호는 기록하지 않는다
        verify(EXPO, PHONE, "0000").andExpect(status().isBadRequest)
        verifiedCount() shouldBe 0L

        verify(EXPO, PHONE, FakeNotificationServer.VALID_CODE).andExpect(status().isOk)
        // 이미 쓴 인증번호로 되풀이해도 건수는 늘지 않는다
        verify(EXPO, PHONE, FakeNotificationServer.VALID_CODE).andExpect(status().isConflict)

        verifiedCount() shouldBe 1L
        val hash = jdbcTemplate.queryForObject("SELECT phone_hash FROM tb_participant_phone_verification", String::class.java)!!
        hash.length shouldBe 64
        hash shouldNotContain PHONE
    }

    @Test
    fun `클라이언트가 보낸 verified 같은 값으로는 세션이 생기지 않는다`() {
        mockMvc
            .perform(
                post("/participant-auth/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"expoId":"$EXPO","phoneNumber":"$PHONE","verified":true}"""),
            ).andExpect(status().isBadRequest)

        redis.keys("participant_session:*").size shouldBe 0
    }

    // --- GET /participant-auth/session

    @Test
    fun `유효한 쿠키는 그 박람회의 세션으로 확인된다`() {
        val sessionId = issueSession(EXPO)

        mockMvc
            .perform(get("/participant-auth/session").param("expoId", EXPO).cookie(Cookie(COOKIE, sessionId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.expoId").value(EXPO))
            .andExpect(jsonPath("$.phoneNumber").doesNotExist())
    }

    @Test
    fun `쿠키가 없으면 SESSION_REQUIRED, 모르는 쿠키는 SESSION_EXPIRED, 다른 박람회의 세션은 SESSION_REQUIRED이다`() {
        val sessionId = issueSession(EXPO)

        mockMvc
            .perform(get("/participant-auth/session").param("expoId", EXPO))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"))
        mockMvc
            .perform(get("/participant-auth/session").param("expoId", EXPO).cookie(Cookie(COOKIE, "garbage")))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"))
        mockMvc
            .perform(get("/participant-auth/session").param("expoId", OTHER_EXPO).cookie(Cookie(COOKIE, sessionId)))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"))
    }

    @Test
    fun `행사 id가 없거나 너무 길면 400이다`() {
        mockMvc.perform(get("/participant-auth/session")).andExpect(status().isBadRequest)
        mockMvc.perform(get("/participant-auth/session").param("expoId", "x".repeat(37))).andExpect(status().isBadRequest)
    }

    // --- DELETE /participant-auth/session

    @Test
    fun `로그아웃하면 세션을 폐기하고 쿠키를 지운다`() {
        val sessionId = issueSession(EXPO)

        val result =
            mockMvc
                .perform(delete("/participant-auth/session").cookie(Cookie(COOKIE, sessionId)))
                .andExpect(status().isNoContent)
                .andReturn()

        result.response.getHeader(HttpHeaders.SET_COOKIE)!! shouldContain "Max-Age=0"
        redis.keys("participant_session:*").size shouldBe 0
        mockMvc
            .perform(get("/participant-auth/session").param("expoId", EXPO).cookie(Cookie(COOKIE, sessionId)))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"))
    }

    @Test
    fun `쿠키가 없어도 로그아웃은 204이다`() {
        mockMvc.perform(delete("/participant-auth/session")).andExpect(status().isNoContent)
    }

    // --- POST /internal/participant-sessions/resolve

    @Test
    fun `내부 조회는 검증된 전화번호를 본문으로 돌려준다`() {
        val sessionId = issueSession(EXPO)

        resolve(sessionId, EXPO)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.expoId").value(EXPO))
            .andExpect(jsonPath("$.phoneNumber").value(PHONE))
            .andExpect(jsonPath("$.expiresAt").value(org.hamcrest.Matchers.matchesPattern(EXPIRES_AT_PATTERN)))
    }

    @Test
    fun `내부 조회는 세션이 없거나 만료되었거나 다른 박람회면 401과 code를 돌려준다`() {
        val sessionId = issueSession(EXPO)

        resolve(null, EXPO).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("SESSION_REQUIRED"))
        resolve("", EXPO).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("SESSION_REQUIRED"))
        resolve("garbage", EXPO).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("SESSION_EXPIRED"))
        resolve(sessionId, OTHER_EXPO).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("SESSION_REQUIRED"))
    }

    @Test
    fun `내부 조회는 토큰이 없거나 틀리면 401이고 참가자 쿠키나 관리자 토큰으로는 열리지 않는다`() {
        val sessionId = issueSession(EXPO)
        val body = """{"sessionId":"$sessionId","expoId":"$EXPO"}"""

        mockMvc
            .perform(post("/internal/participant-sessions/resolve").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)
        mockMvc
            .perform(
                post("/internal/participant-sessions/resolve")
                    .header("X-Internal-Token", "wrong")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isUnauthorized)
        mockMvc
            .perform(
                post("/internal/participant-sessions/resolve")
                    .cookie(Cookie(COOKIE, sessionId))
                    .header("Authorization", bearerOf(1L))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `참가자 쿠키만으로는 관리자 경로에 접근할 수 없다`() {
        val sessionId = issueSession(EXPO)

        mockMvc.perform(get("/admin/my").cookie(Cookie(COOKIE, sessionId))).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/admin").cookie(Cookie(COOKIE, sessionId))).andExpect(status().isUnauthorized)
    }

    private fun issueSession(expoId: String): String {
        sendCode(expoId, PHONE).andExpect(status().isOk)
        return sessionIdOf(verify(expoId, PHONE, FakeNotificationServer.VALID_CODE).andExpect(status().isOk).andReturn())
    }

    private fun sessionIdOf(result: MvcResult): String =
        result.response
            .getHeader(HttpHeaders.SET_COOKIE)!!
            .substringBefore(';')
            .substringAfter("$COOKIE=")

    private fun sendCode(
        expoId: String,
        phone: String,
    ): ResultActions =
        mockMvc.perform(
            post(
                "/participant-auth/code",
            ).contentType(MediaType.APPLICATION_JSON).content("""{"expoId":"$expoId","phoneNumber":"$phone"}"""),
        )

    private fun verify(
        expoId: String,
        phone: String,
        code: String,
    ): ResultActions =
        mockMvc.perform(
            post("/participant-auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"expoId":"$expoId","phoneNumber":"$phone","code":"$code"}"""),
        )

    private fun resolve(
        sessionId: String?,
        expoId: String,
    ): ResultActions {
        val session = if (sessionId == null) "null" else "\"$sessionId\""
        return mockMvc.perform(
            post("/internal/participant-sessions/resolve")
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sessionId":$session,"expoId":"$expoId"}"""),
        )
    }

    private fun clearSessions() {
        redis.keys("participant_session:*").takeIf { it.isNotEmpty() }?.let(redis::delete)
        redis.keys("participant_otp_used:*").takeIf { it.isNotEmpty() }?.let(redis::delete)
    }

    private fun <T> concurrently(request: () -> T): List<T> {
        val start = CountDownLatch(1)
        val futures =
            List(THREADS) {
                executor.submit<T> {
                    start.await()
                    request()
                }
            }
        start.countDown()
        return futures.map { it.get(30, TimeUnit.SECONDS) }
    }

    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-0000000000e1"
        const val OTHER_EXPO = "0199aaaa-0000-7000-8000-0000000000e2"
        const val PHONE = "01012345678"
        const val COOKIE = "PARTICIPANT_SESSION"
        const val THREADS = 8
        const val SESSION_TTL_SECONDS = 1800L
        const val EXPIRES_AT_PATTERN = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}$"
    }
}
