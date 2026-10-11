package team.startup.expo.global.client.notification

import feign.FeignException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import team.startup.expo.global.exception.ExpectedException

/**
 * [NotificationClient]의 실패를 사용자에게 돌려줄 상태로 바꾼다. 인증번호가 틀린 것(400), 요청 내역이 없는 것(404),
 * 횟수 초과(429)는 그대로 전하고, 그 밖의 실패와 연결 불가는 인증 서비스 장애(503)로 돌려준다.
 * 원인 종류만 로그에 남기고 전화번호와 인증번호는 남기지 않는다.
 */
@Component
class NotificationAuthSender(
    private val notificationClient: NotificationClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sendCode(phoneNumber: String) = call { notificationClient.sendCode(NotificationSendCodeReqDto(phoneNumber)) }

    fun verifyCode(
        phoneNumber: String,
        code: String,
    ) = call { notificationClient.verifyCode(NotificationVerifyCodeReqDto(phoneNumber, code)) }

    private fun call(request: () -> Unit) {
        try {
            request()
        } catch (e: FeignException) {
            throw when (e.status()) {
                HttpStatus.BAD_REQUEST.value() -> ExpectedException(HttpStatus.BAD_REQUEST, "인증번호가 일치하지 않습니다.")
                HttpStatus.NOT_FOUND.value() -> ExpectedException(HttpStatus.NOT_FOUND, "인증 요청 내역이 없습니다.")
                HttpStatus.TOO_MANY_REQUESTS.value() -> ExpectedException(HttpStatus.TOO_MANY_REQUESTS, "인증 요청이나 시도 횟수를 초과했습니다.")
                else -> unavailable(e)
            }
        } catch (e: Exception) {
            throw unavailable(e)
        }
    }

    private fun unavailable(e: Exception): ExpectedException {
        log.warn("notification auth call failed: {}", e.javaClass.simpleName)
        return ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "인증 서비스에 연결할 수 없습니다.")
    }
}
