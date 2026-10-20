package team.startup.expo.internal

import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import team.startup.expo.domain.participation.repository.RegistrationOutboxEventRepository
import team.startup.expo.support.IntegrationTestSupport
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.Executors

/**
 * 두 인스턴스가 같은 아웃박스를 동시에 읽는 상황을 DB 연결 두 개(트랜잭션 두 개)로 재현한다. 한쪽이 참가자의 앞 이벤트를
 * 잠그거나 점유하고 발행하는 동안 다른 쪽이 같은 참가자의 뒤 이벤트를 가져가면 안 된다.
 */
class RegistrationOutboxClaimOrderTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var repository: RegistrationOutboxEventRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_registration_outbox RESTART IDENTITY")
    }

    @Test
    fun `다른 트랜잭션이 참가자의 앞 이벤트를 잠그고 있으면 같은 참가자의 뒤 이벤트는 가져가지 않고 다른 참가자의 이벤트만 가져간다`() {
        val first = insert(participantId = 1L)
        insert(participantId = 1L)
        val other = insert(participantId = 2L)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val claimedByB =
                TransactionTemplate(transactionManager).execute {
                    // 인스턴스 A: 맨 앞 이벤트(참가자 1)를 잠그고 발행 중이다
                    repository.findPendingForUpdate(1, LocalDateTime.now()).map { it.eventId } shouldContainExactly listOf(first)
                    // 인스턴스 B: 별도 연결·트랜잭션에서 같은 시각에 읽는다
                    executor
                        .submit<List<String>> {
                            TransactionTemplate(transactionManager).execute {
                                repository.findPendingForUpdate(BATCH, LocalDateTime.now()).map { it.eventId }
                            }
                        }.get()
                }

            claimedByB shouldContainExactly listOf(other)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `다른 인스턴스가 참가자의 앞 이벤트를 점유하고 보내는 중이면 같은 참가자의 뒤 이벤트는 가져가지 않고 다른 참가자의 이벤트만 가져간다`() {
        val first = insert(participantId = 1L)
        insert(participantId = 1L)
        val other = insert(participantId = 2L)

        // 인스턴스 A가 점유를 커밋하고 트랜잭션 밖에서 보내는 중이라 행은 잠겨 있지 않다
        jdbcTemplate.update(
            "UPDATE tb_registration_outbox SET claimed_until = ? WHERE event_id = ?",
            LocalDateTime.now().plusMinutes(1),
            first,
        )

        TransactionTemplate(transactionManager)
            .execute { repository.findPendingForUpdate(BATCH, LocalDateTime.now()).map { it.eventId } } shouldContainExactly listOf(other)
    }

    @Test
    fun `앞 이벤트가 발행되면 같은 참가자의 뒤 이벤트를 가져간다`() {
        val first = insert(participantId = 1L)
        val follower = insert(participantId = 1L)

        jdbcTemplate.update("UPDATE tb_registration_outbox SET published_at = now() WHERE event_id = ?", first)

        TransactionTemplate(transactionManager)
            .execute { repository.findPendingForUpdate(BATCH, LocalDateTime.now()).map { it.eventId } } shouldContainExactly
            listOf(follower)
    }

    private fun insert(participantId: Long): String {
        val eventId = UUID.randomUUID().toString()
        jdbcTemplate.update(
            "INSERT INTO tb_registration_outbox (event_id, event_type, expo_id, participation_type, participant_id, phone_number) " +
                "VALUES (?, 'STANDARD_CREATED', ?, 'STANDARD', ?, NULL)",
            eventId,
            EXPO,
            participantId,
        )
        return eventId
    }

    companion object {
        private const val EXPO = "0199aaaa-0000-7000-8000-0000000000f2"
        private const val BATCH = 50

        @JvmStatic
        @DynamicPropertySource
        fun relayOff(registry: DynamicPropertyRegistry) {
            registry.add("registration-events.relay.enabled") { "false" }
        }
    }
}
