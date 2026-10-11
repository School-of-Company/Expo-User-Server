package team.startup.expo.support

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Notification 서비스의 전화 인증 API(`POST /sms`, `POST /sms/verify`)를 실제 HTTP로 흉내 낸다. 코드를 보낸 번호는 [VALID_CODE]로
 * 검증할 수 있고(실제처럼 같은 코드를 다시 검증해도 성공), 코드를 보낸 적 없는 번호는 404, 코드가 다르면 400이다.
 * [sendStatus], [verifyStatus]가 있으면 그 상태로 실패한다.
 */
object FakeNotificationServer {
    const val VALID_CODE = "1234"

    private val sentTo = CopyOnWriteArrayList<String>()
    private val verifications = CopyOnWriteArrayList<Pair<String, String>>()

    @Volatile
    var sendStatus: Int? = null

    @Volatile
    var verifyStatus: Int? = null

    val sentPhoneNumbers: List<String> get() = sentTo.toList()
    val verifiedRequests: List<Pair<String, String>> get() = verifications.toList()

    private val server: HttpServer by lazy {
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/sms/verify") { handleVerify(it) }
            createContext("/sms") { handleSend(it) }
            start()
        }
    }

    val url: String get() = "http://127.0.0.1:${server.address.port}"

    fun reset() {
        sentTo.clear()
        verifications.clear()
        sendStatus = null
        verifyStatus = null
    }

    private fun handleSend(exchange: HttpExchange) {
        val body = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
        val phone = field(body, "phoneNumber")
        val status = sendStatus ?: 200.also { phone?.let(sentTo::add) }
        respond(exchange, status)
    }

    private fun handleVerify(exchange: HttpExchange) {
        val body = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
        val phone = field(body, "phoneNumber").orEmpty()
        val code = field(body, "code").orEmpty()
        verifications.add(phone to code)
        val status =
            when {
                verifyStatus != null -> verifyStatus!!
                phone !in sentTo -> 404
                code == VALID_CODE -> 200
                else -> 400
            }
        respond(exchange, status)
    }

    private fun field(
        body: String,
        name: String,
    ): String? = Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)

    private fun respond(
        exchange: HttpExchange,
        status: Int,
    ) {
        exchange.sendResponseHeaders(status, -1)
        exchange.close()
    }
}
