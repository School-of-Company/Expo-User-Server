package team.startup.expo.support

import org.testcontainers.kafka.KafkaContainer

/**
 * Kafka를 쓰는 통합 테스트 클래스들이 브로커 하나를 나눠 쓴다. 클래스마다 컨테이너를 새로 띄우면 클래스당 기동 시간이
 * 그대로 더해지기 때문이다. 처음 쓸 때 한 번 띄우고, JVM이 끝나면 Testcontainers가 정리한다.
 *
 * 브로커와 토픽이 클래스 사이에 남으므로, 이 브로커를 읽는 테스트는 토픽 전체가 비어 있다고 가정하지 말고 자기가 보낸
 * 메시지(eventId 등)로 찾아야 한다.
 */
object SharedKafka {
    private val container: KafkaContainer by lazy { KafkaContainer("apache/kafka:3.9.1").also { it.start() } }

    val bootstrapServers: String get() = container.bootstrapServers
}
