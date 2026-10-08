package team.startup.expo.support

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Expo 서비스의 `GET /internal/expo/{expo_id}`를 실제 HTTP로 흉내 낸다. 내부 토큰이 맞아야 응답하고,
 * 등록하지 않은 박람회는 404, [failureStatus]가 있으면 그 상태로 실패한다.
 */
object FakeExpoServer {
    const val TOKEN = "fake-expo-internal-token-0123456789abcdef"

    private val periods = ConcurrentHashMap<String, Pair<String, String>>()
    private val requests = AtomicInteger()

    @Volatile
    var failureStatus: Int? = null

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

    fun reset() {
        periods.clear()
        failureStatus = null
        lastToken = null
        requests.set(0)
    }

    private fun handle(exchange: HttpExchange) {
        requests.incrementAndGet()
        val token = exchange.requestHeaders.getFirst("X-Internal-Token")
        lastToken = token
        val expoId = exchange.requestURI.path.removePrefix("/internal/expo/")
        val period = periods[expoId]
        val (status, body) =
            when {
                token != TOKEN -> 401 to """{"status":401,"message":"인증이 필요합니다."}"""
                failureStatus != null -> failureStatus!! to """{"status":$failureStatus,"message":"fail"}"""
                period == null -> 404 to """{"status":404,"message":"박람회를 찾지 못 했습니다."}"""
                else -> 200 to """{"startedDay":"${period.first}","finishedDay":"${period.second}"}"""
            }
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
