package team.startup.expo.projection

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.kotest.matchers.shouldBe
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import team.startup.expo.global.client.expo.ExpoPeriod
import team.startup.expo.global.client.expo.ExpoPeriodReader
import team.startup.expo.global.client.expo.PreregisterSessionReader
import team.startup.expo.support.FakeExpoServer
import team.startup.expo.support.IntegrationTestSupport
import team.startup.expo.support.SharedKafka
import java.time.Duration
import java.time.LocalDate
import java.util.UUID

/** 박람회 변경 이벤트를 소비해 프로젝션을 만들고, 프로젝션이 켜진 읽기 경로가 프로젝션을 먼저 읽는지 확인한다. */
class ExpoChangedConsumerTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var expoPeriodReader: ExpoPeriodReader

    @Autowired
    private lateinit var preregisterSessionReader: PreregisterSessionReader

    @Autowired
    private lateinit var expoCircuitBreaker: CircuitBreaker

    private lateinit var deadLetterConsumer: KafkaConsumer<String, String>
    private lateinit var expo: String

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_expo_projection CASCADE")
        FakeExpoServer.reset()
        expoCircuitBreaker.reset()
        // 브로커를 다른 테스트와 나눠 쓰고 토픽에 앞선 메시지가 남으므로 테스트마다 다른 박람회를 쓴다
        expo = UUID.randomUUID().toString()
        deadLetterConsumer = consumerOf(DEAD_LETTER_TOPIC)
    }

    @AfterEach
    fun tearDown() {
        deadLetterConsumer.close()
    }

    @Test
    fun `전체 상태 메시지로 기간과 회차를 저장하고 프로젝션에서 읽어 Expo를 부르지 않는다`() {
        send(ExpoChangedPayloads.state(newEventId(), 10, expo, sessions = listOf(7, 9)))
        awaitUntil { version() == 10L }

        expoPeriodReader.find(expo) shouldBe ExpoPeriod(LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 2))
        preregisterSessionReader.exists(expo, 7) shouldBe true
        preregisterSessionReader.exists(expo, 8) shouldBe false
        FakeExpoServer.requestCount shouldBe 0
    }

    @Test
    fun `같은 eventId가 다시 와도 한 번만 반영하고 같거나 낮은 version은 버린다`() {
        val first = newEventId()
        send(ExpoChangedPayloads.state(first, 20, expo, finishedDay = "2030-01-05", sessions = listOf(1)))
        send(ExpoChangedPayloads.state(first, 20, expo, finishedDay = "2030-01-05", sessions = listOf(1)))
        // 늦게 도착한 옛 상태와 같은 version의 다른 상태는 최신 상태를 덮지 못한다
        send(ExpoChangedPayloads.state(newEventId(), 15, expo, finishedDay = "2030-01-03", sessions = listOf(2)))
        send(ExpoChangedPayloads.state(newEventId(), 20, expo, finishedDay = "2030-01-04", sessions = listOf(3)))
        // 같은 키는 같은 파티션에서 순서대로 처리되므로 마지막 메시지가 반영되면 앞의 메시지는 처리가 끝난 것이다
        val last = newEventId()
        send(ExpoChangedPayloads.state(last, 21, expo, finishedDay = "2030-01-05", sessions = listOf(1, 4)))
        awaitUntil { lastEventId() == last }

        version() shouldBe 21L
        expoPeriodReader.find(expo)?.finishedDay shouldBe LocalDate.of(2030, 1, 5)
        sessions() shouldBe listOf(1L, 4L)
    }

    @Test
    fun `tombstone은 기간과 회차를 지우되 version을 남겨 옛 메시지로 되살리지 않는다`() {
        send(ExpoChangedPayloads.state(newEventId(), 30, expo, sessions = listOf(7)))
        send(ExpoChangedPayloads.tombstone(newEventId(), 31, expo))
        send(ExpoChangedPayloads.state(newEventId(), 29, expo, sessions = listOf(7)))
        val marker = newEventId()
        send(ExpoChangedPayloads.tombstone(marker, 32, expo))
        awaitUntil { lastEventId() == marker }

        sessions() shouldBe emptyList()
        expoPeriodReader.find(expo) shouldBe null
        preregisterSessionReader.exists(expo, 7) shouldBe false
        FakeExpoServer.requestCount shouldBe 0
    }

    @Test
    fun `삭제 중인 박람회는 기간은 돌려주지만 회차는 없는 회차로 본다`() {
        send(ExpoChangedPayloads.state(newEventId(), 40, expo, deleting = true, sessions = listOf(7)))
        awaitUntil { version() == 40L }

        expoPeriodReader.find(expo) shouldBe ExpoPeriod(LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 2))
        preregisterSessionReader.exists(expo, 7) shouldBe false
        FakeExpoServer.requestCount shouldBe 0
    }

    @Test
    fun `프로젝션에 없는 박람회는 기존 Expo 호출로 넘어간다`() {
        FakeExpoServer.register(expo, "2030-02-01", "2030-02-02")
        FakeExpoServer.registerSession(expo, 5)

        expoPeriodReader.find(expo) shouldBe ExpoPeriod(LocalDate.of(2030, 2, 1), LocalDate.of(2030, 2, 2))
        preregisterSessionReader.exists(expo, 5) shouldBe true
        FakeExpoServer.requestCount shouldBe 2
    }

    @Test
    fun `해석할 수 없는 메시지는 재시도 없이 이 토픽의 dead letter로 가고 다음 메시지는 정상 처리된다`() {
        val broken = """{"schemaVersion":1,"eventId":"${newEventId()}","version":1,"expoId":"$expo","deleted":false}"""
        kafkaTemplate.send(TOPIC, expo, broken).get()

        awaitRecord(deadLetterConsumer) { it == broken } shouldBe broken
        send(ExpoChangedPayloads.state(newEventId(), 2, expo))
        awaitUntil { version() == 2L }
    }

    private fun send(payload: String) {
        kafkaTemplate.send(TOPIC, expo, payload).get()
    }

    private fun version(): Long? =
        jdbcTemplate.query("SELECT version FROM tb_expo_projection WHERE expo_id = ?", { rs, _ -> rs.getLong(1) }, expo).singleOrNull()

    private fun lastEventId(): String? =
        jdbcTemplate
            .query("SELECT last_event_id FROM tb_expo_projection WHERE expo_id = ?", { rs, _ -> rs.getString(1) }, expo)
            .singleOrNull()

    private fun sessions(): List<Long> =
        jdbcTemplate.query(
            "SELECT session_id FROM tb_expo_projection_preregister_session WHERE expo_id = ? ORDER BY session_id",
            { rs, _ -> rs.getLong(1) },
            expo,
        )

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + AWAIT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(POLL_MILLIS)
        }
        error("기대한 상태가 ${AWAIT_MILLIS}ms 안에 되지 않았습니다.")
    }

    private fun awaitRecord(
        consumer: KafkaConsumer<String, String>,
        matches: (String) -> Boolean,
    ): String {
        val deadline = System.currentTimeMillis() + AWAIT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(500)).forEach { if (matches(it.value())) return it.value() }
        }
        error("기대한 메시지가 ${AWAIT_MILLIS}ms 안에 오지 않았습니다.")
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

    private fun newEventId() = UUID.randomUUID().toString()

    companion object {
        private const val TOPIC = "expo.expo.changed"
        private const val DEAD_LETTER_TOPIC = "expo.expo.changed.dlt"
        private const val AWAIT_MILLIS = 30_000L
        private const val POLL_MILLIS = 200L

        @JvmStatic
        @DynamicPropertySource
        fun enableProjection(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers") { SharedKafka.bootstrapServers }
            registry.add("expo-changed.consumer.auto-startup") { "true" }
            registry.add("expo-projection.enabled") { "true" }
        }
    }
}
