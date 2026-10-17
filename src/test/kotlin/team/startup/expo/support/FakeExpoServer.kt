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
 */
object FakeExpoServer {
    const val TOKEN = "fake-expo-internal-token-0123456789abcdef"

    private val periods = ConcurrentHashMap<String, Pair<String, String>>()
    private val sessions = ConcurrentHashMap<Long, String>()
    private val requests = AtomicInteger()

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
        failureStatus = null
        sessionFailureStatus = null
        lastToken = null
        requests.set(0)
    }

    private fun handle(exchange: HttpExchange) {
        requests.incrementAndGet()
        val token = exchange.requestHeaders.getFirst("X-Internal-Token")
        lastToken = token
        val path = exchange.requestURI.path.removePrefix("/internal/expo/")
        val expoId = path.substringBefore("/preregister-sessions/")
        val sessionId = path.substringAfter("/preregister-sessions/", "").takeIf { it.isNotEmpty() }?.toLongOrNull()
        val period = periods[expoId]
        val (status, body) =
            when {
                token != TOKEN -> {
                    401 to """{"status":401,"message":"인증이 필요합니다."}"""
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
                    200 to """{"startedDay":"${period.first}","finishedDay":"${period.second}"}"""
                }
            }
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
