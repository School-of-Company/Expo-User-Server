package team.startup.expo.support

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Expo 서비스의 `GET /internal/expo/{expo_id}`와 `GET /internal/expo/{expo_id}/preregister-sessions/{session_id}`를
 * 실제 HTTP로 흉내 낸다. 내부 토큰이 맞아야 응답하고, 등록하지 않은 박람회나 그 박람회의 회차가 아니면 404,
 * [failureStatus]가 있으면 그 상태로 실패한다.
 *
 * 박람회 응답의 `features`는 Expo 카탈로그 13개 키를 모두 켠 값이며 [disableFeatures]로 끈 키만 `effectiveEnabled=false`다.
 * [omitFeatures]가 true면 `features` 없는 구형 응답을 흉내 낸다.
 */
object FakeExpoServer {
    const val TOKEN = "fake-expo-internal-token-0123456789abcdef"

    private val periods = ConcurrentHashMap<String, Pair<String, String>>()
    private val sessions = ConcurrentHashMap<Long, String>()
    private val requests = AtomicInteger()
    private val disabled = ConcurrentHashMap<String, Set<String>>()

    private val catalog =
        listOf(
            "PRE_REGISTRATION",
            "FIELD_REGISTRATION",
            "COMPANION_REGISTRATION",
            "QR_ADMISSION",
            "PAPER_QR",
            "STANDARD_PROGRAM",
            "TRAINING_PROGRAM",
            "STANDARD_ATTENDANCE",
            "TRAINING_ATTENDANCE",
            "SURVEY",
            "SURVEY_LOTTERY",
            "AUTO_NOTIFICATION",
            "BULK_MESSAGE",
        )

    @Volatile
    var omitFeatures = false

    @Volatile
    var failureStatus: Int? = null

    /** 회차 조회만 이 상태로 실패시킨다. 박람회 조회는 그대로 성공한다. */
    @Volatile
    var sessionFailureStatus: Int? = null

    @Volatile
    var lastToken: String? = null

    val requestCount: Int get() = requests.get()

    private val server: HttpServer by lazy {
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/internal/expo/") { handle(it) }
            start()
        }
    }

    val url: String get() = "http://127.0.0.1:${server.address.port}"

    fun register(
        expoId: String,
        startedDay: String,
        finishedDay: String,
    ) {
        periods[expoId] = startedDay to finishedDay
    }

    /** 등록 기간 2030-01-01~2030-01-02로 [expoIds] 박람회를 둔다. 기간을 보지 않는 등록 테스트용이다. */
    fun register(vararg expoIds: String) = expoIds.forEach { register(it, "2030-01-01", "2030-01-02") }

    /** [expoId] 박람회의 [keys] 기능을 실효 OFF로 응답한다. */
    fun disableFeatures(
        expoId: String,
        vararg keys: String,
    ) {
        disabled[expoId] = keys.toSet()
    }

    /** [expoId] 박람회의 사전등록 회차 [sessionId]를 둔다. 다른 박람회로 조회하면 Expo처럼 404이다. */
    fun registerSession(
        expoId: String,
        sessionId: Long,
    ) {
        sessions[sessionId] = expoId
    }

    fun reset() {
        periods.clear()
        sessions.clear()
        disabled.clear()
        omitFeatures = false
        failureStatus = null
        sessionFailureStatus = null
        lastToken = null
        requests.set(0)
    }

    private fun handle(exchange: HttpExchange) {
        if (!exchange.requestURI.path.endsWith("/admin-access")) requests.incrementAndGet()
        val token = exchange.requestHeaders.getFirst("X-Internal-Token")
        lastToken = token
        val path = exchange.requestURI.path.removePrefix("/internal/expo/")
        val expoId = path.substringBefore("/preregister-sessions/").substringBefore("/admin-access")
        val sessionId = path.substringAfter("/preregister-sessions/", "").takeIf { it.isNotEmpty() }?.toLongOrNull()
        val period = periods[expoId]
        val (status, body) =
            when {
                token != TOKEN -> {
                    401 to """{"status":401,"message":"인증이 필요합니다."}"""
                }

                path.endsWith("/admin-access") && period != null -> {
                    200 to """{"permission":"APPLICATION_READ","policy":"GLOBAL_ADMIN_APPLICATION_READ_V1","role":"GLOBAL_ADMIN"}"""
                }

                failureStatus != null -> {
                    failureStatus!! to """{"status":$failureStatus,"message":"fail"}"""
                }

                path.contains("/preregister-sessions/") && sessionFailureStatus != null -> {
                    sessionFailureStatus!! to """{"status":$sessionFailureStatus,"message":"fail"}"""
                }

                path.contains("/preregister-sessions/") -> {
                    if (sessionId != null && sessions[sessionId] == expoId) {
                        200 to """{"id":$sessionId,"expoId":"$expoId","title":"1회차","place":"강당","capacity":30}"""
                    } else {
                        404 to """{"status":404,"message":"회차를 찾지 못 했습니다."}"""
                    }
                }

                period == null -> {
                    404 to """{"status":404,"message":"박람회를 찾지 못 했습니다."}"""
                }

                else -> {
                    val off = disabled[expoId].orEmpty()
                    val features =
                        if (omitFeatures) {
                            ""
                        } else {
                            catalog.joinToString(",", ""","features":[""", "]") {
                                """{"key":"$it","configuredEnabled":${it !in off},"effectiveEnabled":${it !in off}}"""
                            }
                        }
                    200 to """{"startedDay":"${period.first}","finishedDay":"${period.second}"$features}"""
                }
            }
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
