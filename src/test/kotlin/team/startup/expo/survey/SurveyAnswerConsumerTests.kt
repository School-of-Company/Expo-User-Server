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
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.domain.training.entity.Trainee
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.support.IntegrationTestSupport
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@Import(SurveyAnswerConsumerTests.KafkaContainerConfig::class)
class SurveyAnswerConsumerTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var kafka: KafkaContainer

    @Autowired
    private lateinit var traineeRepository: TraineeRepository

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    private lateinit var resultConsumer: KafkaConsumer<String, String>
    private lateinit var deadLetterConsumer: KafkaConsumer<String, String>

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE tb_survey_answer_event, tb_trainee_survey_answer, tb_standard_participant_survey_answer, " +
                "tb_trainee, tb_standard_participant RESTART IDENTITY CASCADE",
        )
        traineeRepository.save(
            Trainee(
                expoId = EXPO_ID,
                name = "연수자",
                phoneNumber = "010-1111-2222",
                trainingId = "T-0001",
                informationJson = "{}",
                personalInformationStatus = true,
                applicationType = ApplicationType.PRE,
                applicationDate = LocalDateTime.of(2026, 9, 22, 10, 0),
            ),
        )
        resultConsumer = consumerOf(RESULT_TOPIC)
        deadLetterConsumer = consumerOf(DEAD_LETTER_TOPIC)
    }

    @AfterEach
    fun tearDown() {
        resultConsumer.close()
        deadLetterConsumer.close()
    }

    @Test
    fun `응답자를 찾으면 답변을 저장하고 STORED를 발행한다`() {
        val eventId = newEventId()

        send(eventId, phoneNumber = "01011112222")

        awaitResult(eventId) shouldBe """{"eventId":"$eventId","status":"STORED","reason":null}"""
        count("tb_trainee_survey_answer") shouldBe 1L
        jdbcTemplate.queryForObject("SELECT answer_json ->> '1' FROM tb_trainee_survey_answer", String::class.java) shouldBe "만족"
    }

    @Test
    fun `같은 eventId가 다시 오면 저장 없이 같은 결과를 다시 발행한다`() {
        val eventId = newEventId()

        send(eventId)
        awaitResult(eventId)
        send(eventId)

        awaitResult(eventId) shouldBe """{"eventId":"$eventId","status":"STORED","reason":null}"""
        count("tb_trainee_survey_answer") shouldBe 1L
        count("tb_survey_answer_event") shouldBe 1L
    }

    @Test
    fun `eventId가 달라도 같은 설문과 응답자의 답변은 한 번만 저장하고 STORED로 답한다`() {
        val first = newEventId()
        val second = newEventId()

        send(first)
        awaitResult(first)
        send(second)

        jsonMapper.readTree(awaitResult(second)).get("status").asString() shouldBe "STORED"
        count("tb_trainee_survey_answer") shouldBe 1L
    }

    @Test
    fun `응답자가 없으면 저장하지 않고 REJECTED와 사유를 발행한다`() {
        val eventId = newEventId()

        send(eventId, phoneNumber = "01099999999")

        val result = jsonMapper.readTree(awaitResult(eventId))
        result.get("status").asString() shouldBe "REJECTED"
        result.get("reason").asString() shouldBe "행사 참가자를 찾지 못 했습니다."
        count("tb_trainee_survey_answer") shouldBe 0L
    }

    @Test
    fun `해석할 수 없는 메시지는 재시도 없이 dead letter 토픽으로 간다`() {
        kafkaTemplate.send(SUBMIT_TOPIC, "broken", "not-json").get()

        awaitRecord(deadLetterConsumer) { it == "not-json" } shouldBe "not-json"
        count("tb_survey_answer_event") shouldBe 0L
    }

    @Test
    fun `지원하지 않는 버전은 저장하지 않고 dead letter 토픽으로 간다`() {
        val eventId = newEventId()

        send(eventId, version = 2)

        awaitRecord(deadLetterConsumer) { it.contains(eventId) }
        count("tb_trainee_survey_answer") shouldBe 0L
    }

    private fun send(
        eventId: String,
        phoneNumber: String = "01011112222",
        version: Int = 1,
    ) {
        val value =
            jsonMapper.writeValueAsString(
                mapOf(
                    "eventId" to eventId,
                    "version" to version,
                    "surveyId" to SURVEY_ID,
                    "expoId" to EXPO_ID,
                    "participationType" to "TRAINEE",
                    "phoneNumber" to phoneNumber,
                    "answerJson" to """{"1":"만족"}""",
                    "personalInformationStatus" to true,
                ),
            )
        kafkaTemplate.send(SUBMIT_TOPIC, "$SURVEY_ID:$phoneNumber", value).get()
    }

    private fun awaitResult(eventId: String): String = awaitRecord(resultConsumer) { it.contains("\"eventId\":\"$eventId\"") }

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

    private fun count(table: String): Long = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table", Long::class.java)!!

    private fun newEventId() = UUID.randomUUID().toString()

    @TestConfiguration(proxyBeanMethods = false)
    class KafkaContainerConfig {
        @Bean
        @ServiceConnection
        fun kafka(): KafkaContainer = KafkaContainer("apache/kafka:3.9.1")
    }

    companion object {
        private const val EXPO_ID = "0199aaaa-0000-7000-8000-000000000001"
        private const val SURVEY_ID = "5d1c7f4e-0000-4000-8000-000000000001"
        private const val SUBMIT_TOPIC = "survey.answer.submit"
        private const val RESULT_TOPIC = "survey.answer.result"
        private const val DEAD_LETTER_TOPIC = "survey.answer.submit.dlt"
        private const val AWAIT_MILLIS = 30_000L

        @JvmStatic
        @DynamicPropertySource
        fun enableConsumer(registry: DynamicPropertyRegistry) {
            registry.add("survey-answer.consumer.auto-startup") { "true" }
        }
    }
}
