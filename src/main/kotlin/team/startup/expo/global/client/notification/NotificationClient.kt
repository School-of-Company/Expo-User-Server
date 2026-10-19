package team.startup.expo.global.client.notification

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

/**
 * Notification 서비스의 전화 인증번호(OTP) API를 부른다. 코드 발급, 발송, 검증, 시도 횟수 제한은 Notification이 맡고
 * 이 서비스는 검증에 성공한 결과로 참가자 세션을 만든다. `url`은 비워 두면 Eureka의 `expo-notification-server`로 찾는다.
 *
 * `clients.notification.internal-token`이 있으면 `X-Internal-Token`으로 보내([NotificationClientConfiguration]), Notification이
 * 참가자 인증 발송을 공개 요청과 다른 시간당 상한으로 센다. 전화번호는 요청 본문으로만 보낸다.
 */
@FeignClient(
    name = "expo-notification-server",
    url = "\${clients.notification.url:}",
    configuration = [NotificationClientConfiguration::class],
)
interface NotificationClient {
    @PostMapping("/sms")
    fun sendCode(
        @RequestBody request: NotificationSendCodeReqDto,
    )

    @PostMapping("/sms/verify")
    fun verifyCode(
        @RequestBody request: NotificationVerifyCodeReqDto,
    )
}

data class NotificationSendCodeReqDto(
    val phoneNumber: String,
)

data class NotificationVerifyCodeReqDto(
    val phoneNumber: String,
    val code: String,
) {
    // 인증번호가 로그에 남지 않게 한다
    override fun toString() = "NotificationVerifyCodeReqDto(phoneNumber=***, code=***)"
}
