package team.startup.expo.global.kafka

import org.springframework.boot.context.properties.ConfigurationProperties

/** Expo 서비스가 박람회·프로그램·사전등록 회차를 바꿀 때마다 그 박람회의 전체 상태를 발행하는 토픽 이름. */
@ConfigurationProperties(prefix = "expo-changed")
data class ExpoChangedProperties(
    val topic: String,
    val deadLetterTopic: String,
)
