package team.startup.expo.survey

import io.kotest.matchers.shouldBe
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.kafka.KafkaContainer
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.support.IntegrationTestSupport
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@Import(QrSmsSentConsumerTests.KafkaContainerConfig::class)
class QrSmsSentConsumerTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var kafka: KafkaContainer

    @Autowired
    private lateinit var standardParticipantRepository: StandardParticipantRepository

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    private lateinit var qrDeadLetterConsumer: KafkaConsumer<String, String>
    private lateinit var surveyDeadLetterConsumer: KafkaConsumer<String, String>
    private var firstId = 0L
    private var secondId = 0L

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_expo_deletion, tb_trainee, tb_standard_participant RESTART IDENTITY CASCADE")
        firstId = participant("01011112222")
        secondId = participant("01033334444")
        qrDeadLetterConsumer = consumerOf(QR_DEAD_LETTER_TOPIC)
        surveyDeadLetterConsumer = consumerOf(SURVEY_DEAD_LETTER_TOPIC)
    }

    @AfterEach
    fun tearDown() {
        qrDeadLetterConsumer.close()
        surveyDeadLetterConsumer.close()
    }

    @Test
    fun `발송 완료 이벤트를 소비해 일반 참가자의 발송 횟수를 올린다`() {
        send(newEventId(), firstId)

        awaitUntil { smsTryTime(firstId) == 1 }
        smsTryTime(secondId) shouldBe 0
        count("tb_sms_try_event") shouldBe 1L
    }

    @Test
    fun `같은 eventId가 중복으로 전달되어도 횟수는 한 번만 올라간다`() {
        val duplicated = newEventId()

        send(duplicated, firstId)
        send(duplicated, firstId)
        // 같은 토픽은 순서대로 처리되므로 뒤의 이벤트가 반영되면 앞의 중복 이벤트는 이미 처리를 마친 것이다
        send(newEventId(), secondId)
        awaitUntil { smsTryTime(secondId) == 1 }

        smsTryTime(firstId) shouldBe 1
        count("tb_sms_try_event") shouldBe 2L
    }

    @Test
    fun `해석할 수 없는 메시지는 재시도 없이 이 토픽의 dead letter로 가고 설문의 dead letter로는 가지 않는다`() {
        kafkaTemplate.send(QR_TOPIC, "broken", "not-json").get()

        awaitRecord(qrDeadLetterConsumer) { it == "not-json" } shouldBe "not-json"
        surveyDeadLetterConsumer.poll(Duration.ofSeconds(3)).count() shouldBe 0
        count("tb_sms_try_event") shouldBe 0L
    }

    @Test
    fun `알 수 없는 응답자 구분이나 올바르지 않은 식별자는 dead letter로 가고 다음 이벤트는 정상 처리된다`() {
        val badType = newEventId()
        val badId = newEventId()
        kafkaTemplate.send(QR_TOPIC, badType, payload(badType, firstId, type = "VISITOR")).get()
        kafkaTemplate.send(QR_TOPIC, badId, payload(badId, 0)).get()

        awaitRecord(qrDeadLetterConsumer) { it.contains(badType) }
        send(newEventId(), secondId)
        awaitUntil { smsTryTime(secondId) == 1 }

        smsTryTime(firstId) shouldBe 0
    }

    private fun send(
        eventId: String,
        id: Long,
    ) {
        kafkaTemplate.send(QR_TOPIC, eventId, payload(eventId, id)).get()
    }

    private fun payload(
        eventId: String,
        id: Long,
        type: String = "STANDARD",
    ): String =
        jsonMapper.writeValueAsString(
            mapOf("eventId" to eventId, "expoId" to EXPO, "participationType" to type, "id" to id),
        )

    private fun participant(phone: String): Long =
        standardParticipantRepository
            .save(
                StandardParticipant(
                    expoId = EXPO,
                    name = "참가자",
                    phoneNumber = phone,
                    personalInformationStatus = true,
                    applicationType = ApplicationType.PRE,
                    applicationDate = LocalDateTime.of(2026, 10, 1, 10, 0),
                ),
            ).id!!

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
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "test-${UUID.randomUUID()}",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ),
        ).apply { subscribe(listOf(topic)) }

    private fun smsTryTime(id: Long) =
        jdbcTemplate.queryForObject("SELECT sms_try_time FROM tb_standard_participant WHERE id = ?", Int::class.java, id)

    private fun count(table: String): Long = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table", Long::class.java)!!

    private fun newEventId() = UUID.randomUUID().toString()

    @TestConfiguration(proxyBeanMethods = false)
    class KafkaContainerConfig {
        @Bean
        @ServiceConnection
        fun kafka(): KafkaContainer = KafkaContainer("apache/kafka:3.9.1")
    }

    companion object {
        private const val EXPO = "0199aaaa-0000-7000-8000-0000000000f1"
        private const val QR_TOPIC = "notification.qr-sms.sent"
        private const val QR_DEAD_LETTER_TOPIC = "notification.qr-sms.sent.dlt"
        private const val SURVEY_DEAD_LETTER_TOPIC = "survey.answer.submit.dlt"
        private const val AWAIT_MILLIS = 30_000L
        private const val POLL_MILLIS = 200L

        @JvmStatic
        @DynamicPropertySource
        fun enableConsumer(registry: DynamicPropertyRegistry) {
            registry.add("qr-sms-sent.consumer.auto-startup") { "true" }
        }
    }
}
