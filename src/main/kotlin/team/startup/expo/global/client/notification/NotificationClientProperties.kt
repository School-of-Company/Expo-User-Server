package team.startup.expo.global.client.notification

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Notification `POST /sms`에 `X-Internal-Token`으로 보내는 내부 토큰이다. Notification의 `sms.authInternalToken`과 같은 값이어야
 * 하고, 맞으면 인증번호 시간당 상한을 공개 요청과 따로 센다. 비워 두면 헤더를 보내지 않아 공개 요청과 같은 상한을 쓴다.
 * `url`은 비워 두면 Eureka의 `expo-notification-server`로 찾는다.
 */
@ConfigurationProperties(prefix = "clients.notification")
data class NotificationClientProperties(
    val url: String = "",
    val internalToken: String = "",
) {
    // data class의 기본 toString은 토큰을 그대로 출력한다
    override fun toString() = "NotificationClientProperties(url=$url, internalToken=***)"

    init {
        require(internalToken.isEmpty() || internalToken.length >= MIN_TOKEN_LENGTH) {
            "clients.notification.internal-token must be empty or at least $MIN_TOKEN_LENGTH characters"
        }
    }

    private companion object {
        const val MIN_TOKEN_LENGTH = 32
    }
}
