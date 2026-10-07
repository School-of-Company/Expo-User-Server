package team.startup.expo.internal

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import team.startup.expo.domain.participation.entity.ParticipationType
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.presentation.dto.request.QrSmsSentEvent
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.RecordQrSmsSentService
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.support.IntegrationTestSupport
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RecordQrSmsSentServiceTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var recordQrSmsSentService: RecordQrSmsSentService

    @Autowired
    private lateinit var standardParticipantRepository: StandardParticipantRepository

    private val executor = Executors.newFixedThreadPool(THREADS)
    private var participantId = 0L
    private var otherParticipantId = 0L

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_expo_deletion, tb_trainee, tb_standard_participant RESTART IDENTITY CASCADE")
        participantId = participant(EXPO, "01011112222")
        otherParticipantId = participant(EXPO, "01033334444")
    }

    @AfterEach
    fun tearDown() {
        executor.shutdownNow()
    }

    @Test
    fun `일반 참가자의 발송 완료 이벤트는 발송 횟수를 1 올리고 이벤트를 기록한다`() {
        recordQrSmsSentService.execute(event(newEventId(), participantId))

        smsTryTime(participantId) shouldBe 1
        smsTryTime(otherParticipantId) shouldBe 0
        count("tb_sms_try_event") shouldBe 1L
    }

    @Test
    fun `같은 eventId가 다시 오면 횟수를 다시 올리지 않는다`() {
        val eventId = newEventId()

        repeat(3) { recordQrSmsSentService.execute(event(eventId, participantId)) }

        smsTryTime(participantId) shouldBe 1
        count("tb_sms_try_event") shouldBe 1L
    }

    @Test
    fun `eventId가 다르면 각각 올린다`() {
        recordQrSmsSentService.execute(event(newEventId(), participantId))
        recordQrSmsSentService.execute(event(newEventId(), participantId))

        smsTryTime(participantId) shouldBe 2
    }

    @Test
    fun `같은 eventId를 동시에 소비해도 횟수는 한 번만 올라간다`() {
        val eventId = newEventId()
        val start = CountDownLatch(1)
        val futures =
            List(THREADS) {
                executor.submit {
                    start.await()
                    recordQrSmsSentService.execute(event(eventId, participantId))
                }
            }
        start.countDown()
        futures.forEach { it.get(30, TimeUnit.SECONDS) }

        smsTryTime(participantId) shouldBe 1
        count("tb_sms_try_event") shouldBe 1L
    }

    @Test
    fun `연수자의 발송 완료 이벤트는 횟수를 세지 않는다`() {
        recordQrSmsSentService.execute(event(newEventId(), participantId, ParticipationType.TRAINEE))

        smsTryTime(participantId) shouldBe 0
        count("tb_sms_try_event") shouldBe 0L
    }

    @Test
    fun `삭제 중이거나 삭제된 박람회의 이벤트는 아무것도 쓰지 않는다`() {
        jdbcTemplate.update("INSERT INTO tb_expo_deletion (expo_id, started_at) VALUES (?, now())", EXPO)

        recordQrSmsSentService.execute(event(newEventId(), participantId))

        smsTryTime(participantId) shouldBe 0
        count("tb_sms_try_event") shouldBe 0L
    }

    @Test
    fun `그 박람회에 없는 참가자나 없는 id의 이벤트는 건너뛴다`() {
        val inOtherExpo = participant(OTHER_EXPO, "01055556666")

        // 다른 박람회의 참가자 id를 이 박람회 이벤트에 쓴 경우
        recordQrSmsSentService.execute(event(newEventId(), inOtherExpo))
        // 없는 참가자 id
        recordQrSmsSentService.execute(event(newEventId(), participantId + 1_000))

        smsTryTime(inOtherExpo) shouldBe 0
        smsTryTime(participantId) shouldBe 0
        count("tb_sms_try_event") shouldBe 0L
    }

    @Test
    fun `이미 다른 참가자에 쓴 eventId는 횟수를 올리지 않고 건너뛴다`() {
        val eventId = newEventId()
        recordQrSmsSentService.execute(event(eventId, participantId))

        recordQrSmsSentService.execute(event(eventId, otherParticipantId))

        smsTryTime(participantId) shouldBe 1
        smsTryTime(otherParticipantId) shouldBe 0
        count("tb_sms_try_event") shouldBe 1L
    }

    private fun event(
        eventId: String,
        id: Long,
        type: ParticipationType = ParticipationType.STANDARD,
    ) = QrSmsSentEvent(eventId = eventId, expoId = EXPO, participationType = type, id = id)

    private fun participant(
        expoId: String,
        phone: String,
    ): Long =
        standardParticipantRepository
            .save(
                StandardParticipant(
                    expoId = expoId,
                    name = "참가자",
                    phoneNumber = phone,
                    personalInformationStatus = true,
                    applicationType = ApplicationType.PRE,
                    applicationDate = LocalDateTime.of(2026, 10, 1, 10, 0),
                ),
            ).id!!

    private fun smsTryTime(id: Long) =
        jdbcTemplate.queryForObject("SELECT sms_try_time FROM tb_standard_participant WHERE id = ?", Int::class.java, id)

    private fun count(table: String) = jdbcTemplate.queryForObject("SELECT count(*) FROM $table", Long::class.java)

    private fun newEventId() = UUID.randomUUID().toString()

    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-0000000000e1"
        const val OTHER_EXPO = "0199aaaa-0000-7000-8000-0000000000e2"
        const val THREADS = 8
    }
}
