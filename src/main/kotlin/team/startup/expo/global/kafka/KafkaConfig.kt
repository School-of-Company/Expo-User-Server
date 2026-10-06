package team.startup.expo.global.kafka

import org.apache.kafka.common.TopicPartition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.CommonErrorHandler
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.ExponentialBackOff

@Configuration
class KafkaConfig(
    private val properties: SurveyAnswerProperties,
) {
    /**
     * 일시적 장애는 백오프로 재시도하고, 끝내 처리하지 못한 메시지와 해석할 수 없는 메시지는 dead letter
     * 토픽으로 옮긴다. 파티션은 지정하지 않아(-1) 원본 토픽과 파티션 수가 달라도 보낼 수 있다.
     */
    @Bean
    fun kafkaErrorHandler(kafkaTemplate: KafkaTemplate<String, String>): CommonErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate) { _, _ -> TopicPartition(properties.deadLetterTopic, -1) }
        val backOff =
            ExponentialBackOff(INITIAL_INTERVAL_MS, MULTIPLIER).apply {
                maxInterval = MAX_INTERVAL_MS
                maxAttempts = MAX_RETRIES
            }
        return DefaultErrorHandler(recoverer, backOff).apply {
            addNotRetryableExceptions(MalformedSurveyAnswerEventException::class.java)
        }
    }

    private companion object {
        const val INITIAL_INTERVAL_MS = 1_000L
        const val MULTIPLIER = 2.0
        const val MAX_INTERVAL_MS = 30_000L
        const val MAX_RETRIES = 5L
    }
}

/** 해석할 수 없는 이벤트. 다시 읽어도 같으므로 재시도 없이 dead letter로 보낸다. */
class MalformedSurveyAnswerEventException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
