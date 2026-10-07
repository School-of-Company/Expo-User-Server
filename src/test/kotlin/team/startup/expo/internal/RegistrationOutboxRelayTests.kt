package team.startup.expo.internal

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
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.kafka.KafkaContainer
import team.startup.expo.domain.participation.service.RegistrationOutboxPublisher
import team.startup.expo.support.IntegrationTestSupport
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.UUID

@Import(RegistrationOutboxRelayTests.KafkaContainerConfig::class)
class RegistrationOutboxRelayTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var kafka: KafkaContainer

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    @Autowired
    private lateinit var publisher: RegistrationOutboxPublisher

    private lateinit var registeredConsumer: KafkaConsumer<String, String>
    private lateinit var standardCreatedConsumer: KafkaConsumer<String, String>

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE tb_expo_deletion, tb_registration_request, tb_registration_outbox, tb_trainee, tb_standard_participant " +
                "RESTART IDENTITY CASCADE",
        )
        registeredConsumer = consumerOf(REGISTERED_TOPIC)
        standardCreatedConsumer = consumerOf(STANDARD_CREATED_TOPIC)
    }

    @AfterEach
    fun tearDown() {
        registeredConsumer.close()
        standardCreatedConsumer.close()
    }

    @Test
    fun `일반 참가자 등록은 등록 완료와 신규 저장 이벤트를 각 토픽으로 발행하고 발행 완료로 표시한다`() {
        register(
            """{"expoId":"$EXPO","name":"홍길동","phoneNumber":"$PHONE","informationJson":"{}","personalInformationStatus":true,"applicationType":"PRE"}""",
        )
        val participantId = jdbcTemplate.queryForObject("SELECT id FROM tb_standard_participant", Long::class.java)!!
        val outboxIds =
            jdbcTemplate
                .queryForList(
                    "SELECT event_id FROM tb_registration_outbox ORDER BY id",
                    String::class.java,
                ).map { it!! }

        val registered = jsonMapper.readTree(awaitRecord(registeredConsumer, outboxIds[0]))
        val standardCreated = jsonMapper.readTree(awaitRecord(standardCreatedConsumer, outboxIds[1]))

        registered.get("eventId").asString() shouldBe outboxIds[0]
        registered.get("expoId").asString() shouldBe EXPO
        registered.get("participationType").asString() shouldBe "STANDARD"
        registered.get("id").asLong() shouldBe participantId
        registered.get("phoneNumber").asString() shouldBe PHONE
        standardCreated.get("eventId").asString() shouldBe outboxIds[1]
        standardCreated.get("expoId").asString() shouldBe EXPO
        standardCreated.get("participantId").asLong() shouldBe participantId

        awaitPublishedAll()
        publisher.publishPending() shouldBe 0
    }

    @Test
    fun `연수 현장 등록은 등록 완료 이벤트를 연수자 구분으로 발행한다`() {
        mockMvc
            .perform(
                post("/internal/trainees")
                    .header("X-Internal-Token", INTERNAL_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"expoId":"$EXPO","trainingId":"T-1","name":"연수자","phoneNumber":"$PHONE","informationJson":"{}",""" +
                            """"personalInformationStatus":true,"applicationType":"FIELD"}""",
                    ),
            ).andExpect(status().isCreated)

        val eventId = jdbcTemplate.queryForObject("SELECT event_id FROM tb_registration_outbox", String::class.java)!!

        val registered = jsonMapper.readTree(awaitRecord(registeredConsumer, eventId))

        registered.get("eventId").asString() shouldBe eventId
        registered.get("participationType").asString() shouldBe "TRAINEE"
        registered.get("phoneNumber").asString() shouldBe PHONE
    }

    private fun register(body: String) {
        val request =
            post("/internal/standard-participants")
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        mockMvc.perform(request).andExpect(status().isCreated)
    }

    private fun awaitPublishedAll() {
        val deadline = System.currentTimeMillis() + AWAIT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            val pending =
                jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM tb_registration_outbox WHERE published_at IS NULL",
                    Long::class.java,
                )
            if (pending == 0L) return
            Thread.sleep(200)
        }
        error("아웃박스가 ${AWAIT_MILLIS}ms 안에 모두 발행되지 않았습니다.")
    }

    // 테스트끼리 토픽을 공유하고 컨슈머는 처음부터 읽으므로, 앞 테스트의 레코드를 건너뛰고 이 테스트의 eventId만 찾는다
    private fun awaitRecord(
        consumer: KafkaConsumer<String, String>,
        eventId: String,
    ): String {
        val deadline = System.currentTimeMillis() + AWAIT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(500)).firstOrNull { it.value().contains("\"eventId\":\"$eventId\"") }?.let { return it.value() }
        }
        error("eventId=$eventId 메시지가 ${AWAIT_MILLIS}ms 안에 오지 않았습니다.")
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

    @TestConfiguration(proxyBeanMethods = false)
    class KafkaContainerConfig {
        @Bean
        @ServiceConnection
        fun kafka(): KafkaContainer = KafkaContainer("apache/kafka:3.9.1")
    }

    companion object {
        private const val EXPO = "0199aaaa-0000-7000-8000-0000000000d1"
        private const val PHONE = "01012345678"
        private const val REGISTERED_TOPIC = "participant.registered"
        private const val STANDARD_CREATED_TOPIC = "standard-participant.created"
        private const val AWAIT_MILLIS = 30_000L

        @JvmStatic
        @DynamicPropertySource
        fun enableRelay(registry: DynamicPropertyRegistry) {
            registry.add("registration-events.relay.enabled") { "true" }
            registry.add("registration-events.relay.interval-ms") { "300" }
        }
    }
}
