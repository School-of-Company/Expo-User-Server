package team.startup.expo.global.kafka

import org.springframework.boot.context.properties.ConfigurationProperties

/** 등록 이벤트를 발행하는 토픽 이름. */
@ConfigurationProperties(prefix = "registration-events")
data class RegistrationEventProperties(
    val registeredTopic: String,
    val standardCreatedTopic: String,
)
