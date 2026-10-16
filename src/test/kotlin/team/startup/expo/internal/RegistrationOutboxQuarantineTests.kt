package team.startup.expo.internal

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import team.startup.expo.domain.participation.service.RegistrationOutboxPublisher
import team.startup.expo.domain.participation.service.RegistrationOutboxRelay
import team.startup.expo.support.IntegrationTestSupport
import team.startup.expo.support.SharedKafka
import java.time.Duration
import java.util.UUID

/**
 * 한 이벤트만 계속 실패할 때 릴레이를 여러 주기 돌려 격리·참가자별 순서·재생을 확인한다. 주기를 테스트가 정하려고
 * 스케줄러는 끄고 [RegistrationOutboxRelay.relay]를 직접 부른다.
 */
class RegistrationOutboxQuarantineTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var publisher: RegistrationOutboxPublisher

    private val relay by lazy { RegistrationOutboxRelay(publisher) }

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_registration_outbox RESTART IDENTITY")
    }

    @Test
    fun `Kafka가 계속 거부하는 이벤트는 독립 참가자를 막지 않고 격리되며 재생하면 같은 eventId로 순서대로 나간다`() {
        val rejected = insert(participantId = 1L, participantsJson = participants(1L, "x".repeat(MAX_REQUEST_SIZE)))
        val follower = insert(participantId = 1L, participantsJson = participants(1L, "c"))
        val independent = insert(participantId = 2L)

        repeat(MAX_ATTEMPTS) { relay.relay() }

        isPublished(independent) shouldBe true
        row(rejected)["attempts"] shouldBe MAX_ATTEMPTS
        row(rejected)["last_error"].toString() shouldContain "RecordTooLarge"
        // 그 뒤 주기에는 다른 이벤트가 발행되지 않아 브로커 장애와 구분할 수 없으므로 아직 격리하지 않는다
        isQuarantined(rejected) shouldBe false
        isPublished(follower) shouldBe false

        val later = insert(participantId = 3L)
        relay.relay()

        isPublished(later) shouldBe true
        isQuarantined(rejected) shouldBe true
        relay.relay()
        relay.relay()

        row(rejected)["attempts"] shouldBe MAX_ATTEMPTS + 1
        assertPreservedAndHeld(rejected, follower)

        jdbcTemplate.update("UPDATE tb_registration_outbox SET participants_json = ? WHERE event_id = ?", participants(1L, "c"), rejected)
        assertReplayedInOrder(rejected, follower)
    }

    @Test
    fun `payload를 읽을 수 없는 이벤트는 첫 주기에 격리되고 독립 참가자는 발행되며 재생하면 같은 eventId로 순서대로 나간다`() {
        val broken = insert(participantId = 1L, participantsJson = "{not json")
        val follower = insert(participantId = 1L, participantsJson = participants(1L, "c"))
        val independent = insert(participantId = 2L)

        repeat(3) { relay.relay() }

        isPublished(independent) shouldBe true
        row(broken)["attempts"] shouldBe 1
        isQuarantined(broken) shouldBe true
        assertPreservedAndHeld(broken, follower)

        jdbcTemplate.update("UPDATE tb_registration_outbox SET participants_json = ? WHERE event_id = ?", participants(1L, "c"), broken)
        assertReplayedInOrder(broken, follower)
    }

    /** 격리한 원본은 지우거나 발행 완료로 표시하지 않고, 같은 참가자의 뒤 이벤트는 보내지도 않는다. */
    private fun assertPreservedAndHeld(
        quarantined: String,
        follower: String,
    ) {
        row(quarantined)["event_id"] shouldBe quarantined
        isPublished(quarantined) shouldBe false
        isPublished(follower) shouldBe false
        row(follower)["attempts"] shouldBe 0
        isQuarantined(follower) shouldBe false
    }

    /** V17 마이그레이션에 적은 재생 절차대로 격리를 풀면 같은 eventId로 발행되고 같은 참가자의 뒤 이벤트가 그 뒤에 나간다. */
    private fun assertReplayedInOrder(
        quarantined: String,
        follower: String,
    ) {
        jdbcTemplate.update(
            "UPDATE tb_registration_outbox SET quarantined_at = NULL WHERE event_id = ? AND published_at IS NULL",
            quarantined,
        )

        relay.relay()

        isPublished(quarantined) shouldBe true
        isPublished(follower) shouldBe true
        awaitRegisteredEventIds(setOf(quarantined, follower)) shouldBe listOf(quarantined, follower)
    }

    private fun insert(
        participantId: Long,
        participantsJson: String? = null,
    ): String {
        val eventId = UUID.randomUUID().toString()
        val type = if (participantsJson == null) "STANDARD_CREATED" else "REGISTERED"
        jdbcTemplate.update(
            "INSERT INTO tb_registration_outbox (event_id, event_type, expo_id, participation_type, participant_id, phone_number, " +
                "participants_json) VALUES (?, ?, ?, 'STANDARD', ?, ?, ?)",
            eventId,
            type,
            EXPO,
            participantId,
            PHONE,
            participantsJson,
        )
        return eventId
    }

    private fun participants(
        id: Long,
        code: String,
    ) = """[{"id":$id,"code":"$code"}]"""

    private fun row(eventId: String) = jdbcTemplate.queryForMap("SELECT * FROM tb_registration_outbox WHERE event_id = ?", eventId)

    private fun isPublished(eventId: String) = row(eventId)["published_at"] != null

    private fun isQuarantined(eventId: String) = row(eventId)["quarantined_at"] != null

    // 토픽을 다른 테스트와 공유하므로 이 테스트의 eventId만 파티션 순서대로 모은다. 같은 참가자는 키가 같아 한 파티션에 들어간다
    private fun awaitRegisteredEventIds(eventIds: Set<String>): List<String> {
        val found = mutableListOf<String>()
        consumerOf(REGISTERED_TOPIC).use { consumer ->
            val deadline = System.currentTimeMillis() + AWAIT_MILLIS
            while (found.size < eventIds.size && System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach { record ->
                    eventIds.firstOrNull { record.value().contains("\"eventId\":\"$it\"") }?.let(found::add)
                }
            }
        }
        return found
    }

    private fun consumerOf(topic: String): KafkaConsumer<String, String> =
        KafkaConsumer<String, String>(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to SharedKafka.bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "test-${UUID.randomUUID()}",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ),
        ).apply { subscribe(listOf(topic)) }

    companion object {
        private const val EXPO = "0199aaaa-0000-7000-8000-0000000000f1"
        private const val PHONE = "01012345678"
        private const val REGISTERED_TOPIC = "user.participant.registered"
        private const val MAX_ATTEMPTS = 5
        private const val MAX_REQUEST_SIZE = 4096
        private const val AWAIT_MILLIS = 30_000L

        @JvmStatic
        @DynamicPropertySource
        fun kafka(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers") { SharedKafka.bootstrapServers }
            registry.add("registration-events.relay.enabled") { "false" }
            // 이보다 큰 레코드는 프로듀서가 매번 거부한다(RecordTooLargeException)
            registry.add("spring.kafka.producer.properties[max.request.size]") { MAX_REQUEST_SIZE.toString() }
        }
    }
}
