package team.startup.expo.internal

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionTemplate
import team.startup.expo.domain.participation.repository.RegistrationOutboxEventRepository
import team.startup.expo.domain.participation.service.PurgeExpoDataService
import team.startup.expo.domain.participation.service.RegistrationOutboxPublisher
import team.startup.expo.global.kafka.RegistrationEventProperties
import team.startup.expo.support.IntegrationTestSupport
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 아웃박스 발행이 Kafka 응답을 기다리는 동안 행을 잠그고 있지 않은지 확인한다. Kafka 전송은 테스트가 끝내 줄 때까지
 * 멈추는 가짜로 바꾸고, 발행기는 실제 DB와 트랜잭션 위에서 돌린다.
 */
class RegistrationOutboxLeaseTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var repository: RegistrationOutboxEventRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    @Autowired
    private lateinit var properties: RegistrationEventProperties

    @Autowired
    private lateinit var purgeExpoDataService: PurgeExpoDataService

    private val kafkaTemplate = mockk<KafkaTemplate<String, String>>()

    private val publisher by lazy { RegistrationOutboxPublisher(repository, kafkaTemplate, jsonMapper, properties, transactionTemplate) }

    private val executor = Executors.newCachedThreadPool()

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_registration_outbox, tb_expo_deletion RESTART IDENTITY")
    }

    @AfterEach
    fun tearDown() {
        executor.shutdownNow()
    }

    @Test
    fun `Kafka 응답을 기다리는 동안 박람회 삭제는 기다리지 않고 아웃박스를 지우며 발행 결과가 지운 행을 되살리지 않는다`() {
        val eventId = insert()
        val pendingSend = blockSend()

        val publishing = executor.submit<Int> { publisher.publishPending() }
        pendingSend.awaitSending()

        executor.submit { purgeExpoDataService.execute(EXPO) }.get(PURGE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        pendingSend.complete()

        publishing.get(AWAIT_SECONDS, TimeUnit.SECONDS) shouldBe 1
        count(eventId) shouldBe 0
    }

    @Test
    fun `전송 중인 이벤트는 다른 회차가 가져가지 않고 끝나면 발행 완료로 남아 다시 나가지 않는다`() {
        val eventId = insert()
        val pendingSend = blockSend()

        val publishing = executor.submit<Int> { publisher.publishPending() }
        pendingSend.awaitSending()

        publisher.publishPending() shouldBe 0
        pendingSend.complete()

        publishing.get(AWAIT_SECONDS, TimeUnit.SECONDS) shouldBe 1
        isPublished(eventId) shouldBe true
        claimedUntil(eventId) shouldBe null
        publisher.publishPending() shouldBe 0
        pendingSend.sends shouldBe 1
    }

    @Test
    fun `전송 도중 죽어 결과를 남기지 못한 이벤트는 점유 기한이 지나면 다시 가져가 같은 eventId로 발행한다`() {
        val eventId = insert()
        jdbcTemplate.update(
            "UPDATE tb_registration_outbox SET claimed_until = ? WHERE event_id = ?",
            LocalDateTime.now().minusSeconds(1),
            eventId,
        )
        val pendingSend = blockSend().apply { complete() }

        publisher.publishPending() shouldBe 1

        isPublished(eventId) shouldBe true
        pendingSend.sends shouldBe 1
    }

    @Test
    fun `전송이 실패하면 시도 횟수와 오류를 남기고 점유를 풀어 다음 회차에 다시 보낸다`() {
        val eventId = insert()
        every { kafkaTemplate.send(any<String>(), any<String>(), any()) } returns
            CompletableFuture.failedFuture(IllegalStateException("broker down"))

        publisher.publishPending() shouldBe 0

        val row =
            jdbcTemplate.queryForMap(
                "SELECT attempts, last_error, published_at, claimed_until FROM tb_registration_outbox WHERE event_id = ?",
                eventId,
            )
        row["attempts"] shouldBe 1
        (row["last_error"] != null) shouldBe true
        row["published_at"] shouldBe null
        row["claimed_until"] shouldBe null
    }

    private inner class BlockedSend {
        private val result = CompletableFuture<SendResult<String, String>>()
        private val sending = CountDownLatch(1)

        @Volatile
        var sends = 0

        init {
            every { kafkaTemplate.send(any<String>(), any<String>(), any()) } answers {
                sends += 1
                sending.countDown()
                result
            }
        }

        fun awaitSending() {
            sending.await(AWAIT_SECONDS, TimeUnit.SECONDS) shouldBe true
        }

        fun complete() {
            result.complete(mockk())
        }
    }

    private fun blockSend() = BlockedSend()

    private fun insert(): String {
        val eventId = UUID.randomUUID().toString()
        jdbcTemplate.update(
            "INSERT INTO tb_registration_outbox (event_id, event_type, expo_id, participation_type, participant_id, phone_number) " +
                "VALUES (?, 'STANDARD_CREATED', ?, 'STANDARD', 1, NULL)",
            eventId,
            EXPO,
        )
        return eventId
    }

    private fun count(eventId: String): Int =
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_registration_outbox WHERE event_id = ?", Int::class.java, eventId)!!

    private fun isPublished(eventId: String): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT published_at IS NOT NULL FROM tb_registration_outbox WHERE event_id = ?",
            Boolean::class.java,
            eventId,
        )!!

    private fun claimedUntil(eventId: String): Any? =
        jdbcTemplate.queryForMap("SELECT claimed_until FROM tb_registration_outbox WHERE event_id = ?", eventId)["claimed_until"]

    companion object {
        private const val EXPO = "0199aaaa-0000-7000-8000-0000000000f3"
        private const val AWAIT_SECONDS = 5L

        // Expo의 박람회 삭제 호출 제한(5초)보다 짧게 기다린다
        private const val PURGE_TIMEOUT_SECONDS = 3L

        @JvmStatic
        @DynamicPropertySource
        fun relayOff(registry: DynamicPropertyRegistry) {
            registry.add("registration-events.relay.enabled") { "false" }
        }
    }
}
