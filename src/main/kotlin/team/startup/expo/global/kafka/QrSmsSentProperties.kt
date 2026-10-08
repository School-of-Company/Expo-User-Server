package team.startup.expo.global.kafka

import org.springframework.boot.context.properties.ConfigurationProperties

/** Notification 서비스가 QR 문자 발송에 성공한 뒤 발행하는 발송 완료 이벤트의 토픽 이름. */
@ConfigurationProperties(prefix = "qr-sms-sent")
data class QrSmsSentProperties(
    val topic: String,
    val deadLetterTopic: String,
)
