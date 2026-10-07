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
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.kafka.KafkaContainer
import team.startup.expo.domain.participation.service.ExpoDeletionLock
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.domain.training.entity.Trainee
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.support.IntegrationTestSupport
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    private lateinit var resultConsumer: KafkaConsumer<String, String>
    private lateinit var deadLetterConsumer: KafkaConsumer<String, String>

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE tb_expo_deletion, tb_survey_answer_event, tb_trainee_survey_answer, tb_standard_participant_survey_answer, " +
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

        send(eventId, version = 3)

        awaitRecord(deadLetterConsumer) { it.contains(eventId) }
        count("tb_trainee_survey_answer") shouldBe 0L
    }

    @Test
    fun `v2 이벤트는 제출 당시 문항 스냅샷을 답변과 함께 저장한다`() {
        val eventId = newEventId()

        send(eventId, version = 2, questions = QUESTIONS)

        awaitResult(eventId) shouldBe """{"eventId":"$eventId","status":"STORED","reason":null}"""
        answerSnapshotTitle(SURVEY_ID) shouldBe "만족도"
        // 답변은 문항 ID를 키로 하는 그대로이고 스냅샷은 별도 컬럼이다
        jdbcTemplate.queryForObject("SELECT answer_json ->> '1' FROM tb_trainee_survey_answer", String::class.java) shouldBe "만족"
    }

    @Test
    fun `v1 이벤트는 스냅샷 없이 저장하고 v1과 v2가 섞여도 각각 처리한다`() {
        val v1 = newEventId()
        val v2 = newEventId()

        send(v1)
        send(v2, version = 2, surveyId = OTHER_SURVEY_ID, questions = QUESTIONS)

        awaitResult(v1)
        awaitResult(v2)
        count("tb_trainee_survey_answer") shouldBe 2L
        answerSnapshotTitle(SURVEY_ID) shouldBe null
        answerSnapshotTitle(OTHER_SURVEY_ID) shouldBe "만족도"
    }

    @Test
    fun `같은 eventId의 v2 이벤트가 다시 오면 저장 없이 같은 결과를 주고 스냅샷도 그대로이다`() {
        val eventId = newEventId()

        send(eventId, version = 2, questions = QUESTIONS)
        awaitResult(eventId)
        send(eventId, version = 2, questions = listOf(mapOf("id" to "9", "title" to "바뀜")))

        awaitResult(eventId) shouldBe """{"eventId":"$eventId","status":"STORED","reason":null}"""
        count("tb_trainee_survey_answer") shouldBe 1L
        answerSnapshotTitle(SURVEY_ID) shouldBe "만족도"
    }

    @Test
    fun `questions가 배열이 아닌 이벤트는 저장하지 않고 dead letter 토픽으로 간다`() {
        val eventId = newEventId()

        send(eventId, version = 2, questions = mapOf("id" to "1", "title" to "만족도"))

        awaitRecord(deadLetterConsumer) { it.contains(eventId) }
        count("tb_trainee_survey_answer") shouldBe 0L
        count("tb_survey_answer_event") shouldBe 0L
    }

    @Test
    fun `삭제된 박람회의 이벤트는 기록과 답변과 결과 없이 건너뛴다`() {
        jdbcTemplate.update("INSERT INTO tb_expo_deletion (expo_id, started_at) VALUES (?, now())", EXPO_ID)
        val skipped = newEventId()
        val alive = newEventId()
        saveTrainee(OTHER_EXPO_ID, "01033334444")

        send(skipped)
        // 같은 토픽은 순서대로 처리되므로 뒤의 이벤트 결과가 오면 앞의 이벤트는 이미 처리를 마친 것이다
        send(alive, phoneNumber = "01033334444", expoId = OTHER_EXPO_ID)
        val published = collectResultsUntil(alive)

        published.none { it.contains(skipped) } shouldBe true
        count("tb_survey_answer_event") shouldBe 1L
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_survey_answer_event WHERE event_id = ?", Long::class.java, skipped) shouldBe 0L
        count("tb_trainee_survey_answer") shouldBe 1L
    }

    @Test
    fun `삭제가 커밋되기 전에 도착한 이벤트는 삭제가 끝나길 기다렸다가 건너뛴다`() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val locked = CountDownLatch(1)
            val release = CountDownLatch(1)
            val deleting =
                executor.submit {
                    transactionTemplate.executeWithoutResult {
                        // 삭제 트랜잭션처럼 배타 lock을 잡고 삭제 기록을 남긴 뒤 아직 커밋하지 않는다
                        jdbcTemplate.query("SELECT pg_advisory_xact_lock(?, hashtext(?))", { _ -> }, ExpoDeletionLock.NAMESPACE, EXPO_ID)
                        jdbcTemplate.update("INSERT INTO tb_expo_deletion (expo_id, started_at) VALUES (?, now())", EXPO_ID)
                        locked.countDown()
                        release.await(20, TimeUnit.SECONDS)
                    }
                }
            locked.await(10, TimeUnit.SECONDS) shouldBe true
            val skipped = newEventId()
            send(skipped)

            Thread.sleep(BLOCK_CHECK_MILLIS)
            // 소비가 삭제의 lock에 막혀 있으므로 그 사이에는 아무것도 저장하지 않는다
            count("tb_survey_answer_event") shouldBe 0L
            count("tb_trainee_survey_answer") shouldBe 0L

            release.countDown()
            deleting.get(10, TimeUnit.SECONDS)

            val alive = newEventId()
            saveTrainee(OTHER_EXPO_ID, "01033334444")
            send(alive, phoneNumber = "01033334444", expoId = OTHER_EXPO_ID)
            collectResultsUntil(alive).none { it.contains(skipped) } shouldBe true
            // 삭제 뒤에 소비를 마쳐도 삭제된 박람회의 기록은 남지 않는다
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tb_survey_answer_event WHERE event_id = ?",
                Long::class.java,
                skipped,
            ) shouldBe
                0L
            count("tb_trainee_survey_answer") shouldBe 1L
        } finally {
            executor.shutdownNow()
        }
    }

    private fun saveTrainee(
        expoId: String,
        phoneNumber: String,
    ) {
        traineeRepository.save(
            Trainee(
                expoId = expoId,
                name = "다른연수자",
                phoneNumber = phoneNumber,
                trainingId = "T-0002",
                informationJson = "{}",
                personalInformationStatus = true,
                applicationType = ApplicationType.PRE,
                applicationDate = LocalDateTime.of(2026, 9, 22, 10, 0),
            ),
        )
    }

    /** [eventId]의 결과가 올 때까지 결과 토픽에서 읽은 메시지를 모두 돌려준다. */
    private fun collectResultsUntil(eventId: String): List<String> {
        val collected = mutableListOf<String>()
        awaitRecord(resultConsumer) {
            collected += it
            it.contains("\"eventId\":\"$eventId\"")
        }
        return collected
    }

    private fun send(
        eventId: String,
        phoneNumber: String = "01011112222",
        version: Int = 1,
        expoId: String = EXPO_ID,
        surveyId: String = SURVEY_ID,
        questions: Any? = null,
    ) {
        val payload =
            mutableMapOf<String, Any?>(
                "eventId" to eventId,
                "version" to version,
                "surveyId" to surveyId,
                "expoId" to expoId,
                "participationType" to "TRAINEE",
                "phoneNumber" to phoneNumber,
                "answerJson" to """{"1":"만족"}""",
                "personalInformationStatus" to true,
            )
        // v1 이벤트에는 questions 필드 자체가 없다
        if (questions != null) payload["questions"] = questions
        val value = jsonMapper.writeValueAsString(payload)
        kafkaTemplate.send(SUBMIT_TOPIC, "$surveyId:$phoneNumber", value).get()
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

    private fun answerSnapshotTitle(surveyId: String): String? =
        jdbcTemplate.queryForObject(
            "SELECT answer_questions -> 0 ->> 'title' FROM tb_trainee_survey_answer WHERE survey_id = ?",
            String::class.java,
            surveyId,
        )

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
        private const val OTHER_EXPO_ID = "0199aaaa-0000-7000-8000-000000000002"
        private const val BLOCK_CHECK_MILLIS = 2_000L
        private const val SURVEY_ID = "5d1c7f4e-0000-4000-8000-000000000001"
        private const val SUBMIT_TOPIC = "survey.answer.submit"
        private const val RESULT_TOPIC = "survey.answer.result"
        private const val DEAD_LETTER_TOPIC = "survey.answer.submit.dlt"
        private const val AWAIT_MILLIS = 30_000L
        private const val OTHER_SURVEY_ID = "5d1c7f4e-0000-4000-8000-000000000002"
        private val QUESTIONS = listOf(mapOf("id" to "1", "title" to "만족도", "order" to 0, "formType" to "SENTENCE"))

        @JvmStatic
        @DynamicPropertySource
        fun enableConsumer(registry: DynamicPropertyRegistry) {
            registry.add("survey-answer.consumer.auto-startup") { "true" }
        }
    }
}
