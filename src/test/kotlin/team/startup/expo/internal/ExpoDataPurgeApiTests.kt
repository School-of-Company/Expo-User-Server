package team.startup.expo.internal

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.support.TransactionTemplate
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.ExpoDeletionLock
import team.startup.expo.domain.participation.service.PurgeExpoDataService
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.domain.training.entity.Trainee
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.support.IntegrationTestSupport
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ExpoDataPurgeApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var traineeRepository: TraineeRepository

    @Autowired
    private lateinit var standardParticipantRepository: StandardParticipantRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var purgeExpoDataService: PurgeExpoDataService

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    private val executor = Executors.newFixedThreadPool(2)
    private lateinit var expoA: String
    private lateinit var expoB: String

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_expo_deletion, tb_trainee, tb_standard_participant, tb_admin RESTART IDENTITY CASCADE")
        // 삭제 기록은 영구히 남으므로 테스트마다 새 박람회 id를 쓴다
        expoA = UUID.randomUUID().toString()
        expoB = UUID.randomUUID().toString()
    }

    @AfterEach
    fun tearDown() {
        executor.shutdownNow()
    }

    @Test
    fun `박람회의 연수자, 일반 참가자, 입장 기록, 설문 답변을 모두 지우고 다른 박람회는 그대로 둔다`() {
        seed(expoA)
        seed(expoB)

        purge(expoA).andExpect(status().isNoContent)

        listOf("tb_trainee", "tb_standard_participant", "tb_trainee_participation", "tb_standard_participant_participation").forEach {
            count(it, expoA) shouldBe 0
            count(it, expoB) shouldBe 1
        }
        // 설문 답변에는 expo_id가 없어 참가자를 거쳐 센다
        countAnswers("tb_trainee_survey_answer", "tb_trainee", expoA) shouldBe 0
        countAnswers("tb_standard_participant_survey_answer", "tb_standard_participant", expoA) shouldBe 0
        countAnswers("tb_trainee_survey_answer", "tb_trainee", expoB) shouldBe 1
        countAnswers("tb_standard_participant_survey_answer", "tb_standard_participant", expoB) shouldBe 1
        // 삭제 시작과 완료가 기록된다
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM tb_expo_deletion WHERE expo_id = ? AND completed_at IS NOT NULL",
            Long::class.java,
            expoA,
        ) shouldBe
            1L
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_expo_deletion WHERE expo_id = ?", Long::class.java, expoB) shouldBe 0L
    }

    @Test
    fun `이미 지웠거나 데이터가 없어도 여러 번 불러도 204이다`() {
        seed(expoA)

        purge(expoA).andExpect(status().isNoContent)
        purge(expoA).andExpect(status().isNoContent)
        purge(expoB).andExpect(status().isNoContent)

        count("tb_trainee", expoA) shouldBe 0
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_expo_deletion WHERE expo_id = ?", Long::class.java, expoA) shouldBe 1L
    }

    @Test
    fun `토큰이 없거나 틀리거나 관리자 토큰만 있으면 401이고 아무것도 지우지 않는다`() {
        seed(expoA)
        val adminId =
            adminRepository
                .save(
                    Admin(
                        name = "관리자",
                        nickname = "admin",
                        email = "admin@gsm.hs.kr",
                        password = "encoded",
                        phoneNumber = "01000000000",
                    ).apply {
                        accept()
                    },
                ).id!!

        mockMvc.perform(delete("/internal/expos/$expoA")).andExpect(status().isUnauthorized)
        mockMvc.perform(delete("/internal/expos/$expoA").header("X-Internal-Token", "wrong-token")).andExpect(status().isUnauthorized)
        mockMvc.perform(delete("/internal/expos/$expoA").header("Authorization", bearerOf(adminId))).andExpect(status().isUnauthorized)

        count("tb_trainee", expoA) shouldBe 1
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_expo_deletion", Long::class.java) shouldBe 0L
    }

    @Test
    fun `박람회 id가 너무 길면 400이고 삭제하지 않는다`() {
        mockMvc
            .perform(delete("/internal/expos/${"a".repeat(37)}").header("X-Internal-Token", INTERNAL_TOKEN))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `삭제한 박람회에는 늦게 도착한 쓰기로 연수자와 참가자를 다시 만들 수 없다`() {
        purge(expoA).andExpect(status().isNoContent)

        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException::class.java) {
            traineeRepository.saveAndFlush(trainee(expoA))
        }
        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException::class.java) {
            standardParticipantRepository.saveAndFlush(participant(expoA))
        }
        count("tb_trainee", expoA) shouldBe 0
        count("tb_standard_participant", expoA) shouldBe 0
    }

    @Test
    fun `이미 있는 참가자를 삭제한 박람회로 옮기는 수정도 막는다`() {
        purge(expoA).andExpect(status().isNoContent)
        val traineeInB = traineeRepository.saveAndFlush(trainee(expoB)).id!!

        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException::class.java) {
            jdbcTemplate.update("UPDATE tb_trainee SET expo_id = ? WHERE id = ?", expoA, traineeInB)
        }
        jdbcTemplate.queryForObject("SELECT expo_id FROM tb_trainee WHERE id = ?", String::class.java, traineeInB) shouldBe expoB
    }

    @Test
    fun `다른 박람회의 쓰기는 삭제의 영향을 받지 않는다`() {
        purge(expoA).andExpect(status().isNoContent)

        traineeRepository.saveAndFlush(trainee(expoB))
        standardParticipantRepository.saveAndFlush(participant(expoB))

        count("tb_trainee", expoB) shouldBe 1
        count("tb_standard_participant", expoB) shouldBe 1
    }

    @Test
    fun `쓰기가 먼저 진행 중이면 삭제는 그 커밋을 기다렸다가 그 행까지 지운다`() {
        val inserted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writer =
            executor.submit {
                transactionTemplate.executeWithoutResult {
                    traineeRepository.saveAndFlush(trainee(expoA))
                    inserted.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
            }
        inserted.await(10, TimeUnit.SECONDS) shouldBe true

        val purging = executor.submit { purgeExpoDataService.execute(expoA) }
        Thread.sleep(BLOCK_CHECK_MILLIS)
        purging.isDone shouldBe false

        release.countDown()
        writer.get(10, TimeUnit.SECONDS)
        purging.get(10, TimeUnit.SECONDS)
        // 커밋된 쓰기가 삭제 뒤에 살아남으면 안 된다
        count("tb_trainee", expoA) shouldBe 0
    }

    @Test
    fun `삭제가 먼저 진행 중이면 쓰기는 기다렸다가 거부되어 행이 남지 않는다`() {
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val deleting =
            executor.submit {
                transactionTemplate.executeWithoutResult {
                    jdbcTemplate.query("SELECT pg_advisory_xact_lock(?, hashtext(?))", { _ -> }, ExpoDeletionLock.NAMESPACE, expoA)
                    jdbcTemplate.update("INSERT INTO tb_expo_deletion (expo_id, started_at) VALUES (?, now())", expoA)
                    locked.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
            }
        locked.await(10, TimeUnit.SECONDS) shouldBe true

        val writing =
            executor.submit<Throwable?> {
                runCatching {
                    transactionTemplate.executeWithoutResult {
                        traineeRepository.saveAndFlush(
                            trainee(expoA),
                        )
                    }
                }.exceptionOrNull()
            }
        Thread.sleep(BLOCK_CHECK_MILLIS)
        // 삭제 기록이 아직 커밋되지 않아도 쓰기는 삭제의 lock에 막혀 기다린다
        writing.isDone shouldBe false

        release.countDown()
        deleting.get(10, TimeUnit.SECONDS)
        (writing.get(10, TimeUnit.SECONDS) is DataIntegrityViolationException) shouldBe true
        count("tb_trainee", expoA) shouldBe 0
    }

    @Test
    fun `expo_id를 바꾸지 않는 수정은 진행 중인 삭제의 lock을 기다리지 않는다`() {
        val traineeId = traineeRepository.saveAndFlush(trainee(expoA)).id!!
        val participantId = standardParticipantRepository.saveAndFlush(participant(expoA)).id!!
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val deleting =
            executor.submit {
                transactionTemplate.executeWithoutResult {
                    jdbcTemplate.query("SELECT pg_advisory_xact_lock(?, hashtext(?))", { _ -> }, ExpoDeletionLock.NAMESPACE, expoA)
                    locked.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
            }
        locked.await(10, TimeUnit.SECONDS) shouldBe true

        // Hibernate의 일반 UPDATE처럼 같은 expo_id를 SET 절에 포함해도 공유 lock을 잡지 않아야 교착 상태가 생기지 않는다
        val updating =
            executor.submit {
                jdbcTemplate.update("UPDATE tb_trainee SET expo_id = ?, name = ? WHERE id = ?", expoA, "수정", traineeId)
                jdbcTemplate.update("UPDATE tb_standard_participant SET expo_id = ?, name = ? WHERE id = ?", expoA, "수정", participantId)
            }
        try {
            updating.get(BLOCK_CHECK_MILLIS * 4, TimeUnit.MILLISECONDS)
        } finally {
            release.countDown()
            deleting.get(10, TimeUnit.SECONDS)
        }
        nameOf("tb_trainee", traineeId) shouldBe "수정"
        nameOf("tb_standard_participant", participantId) shouldBe "수정"
    }

    private fun purge(expoId: String) = mockMvc.perform(delete("/internal/expos/$expoId").header("X-Internal-Token", INTERNAL_TOKEN))

    private fun seed(expoId: String) {
        val traineeId = traineeRepository.saveAndFlush(trainee(expoId)).id!!
        val participantId = standardParticipantRepository.saveAndFlush(participant(expoId)).id!!
        jdbcTemplate.update(
            "INSERT INTO tb_trainee_participation (entry_time, attendance_date, trainee_id, expo_id) VALUES (now(), ?, ?, ?)",
            LocalDate.of(2026, 10, 1),
            traineeId,
            expoId,
        )
        jdbcTemplate.update(
            "INSERT INTO tb_standard_participant_participation (entry_time, attendance_date, standard_participant_id, expo_id) VALUES (now(), ?, ?, ?)",
            LocalDate.of(2026, 10, 1),
            participantId,
            expoId,
        )
        jdbcTemplate.update(
            "INSERT INTO tb_trainee_survey_answer (survey_id, trainee_id, answer_json, personal_information_status) VALUES (?, ?, '{\"1\":\"a\"}'::jsonb, true)",
            UUID.randomUUID().toString(),
            traineeId,
        )
        jdbcTemplate.update(
            "INSERT INTO tb_standard_participant_survey_answer (survey_id, standard_participant_id, answer_json, personal_information_status) VALUES (?, ?, '{\"1\":\"a\"}'::jsonb, true)",
            UUID.randomUUID().toString(),
            participantId,
        )
    }

    private fun count(
        table: String,
        expoId: String,
    ) = jdbcTemplate.queryForObject("SELECT count(*) FROM $table WHERE expo_id = ?", Long::class.java, expoId)

    private fun nameOf(
        table: String,
        id: Long,
    ) = jdbcTemplate.queryForObject("SELECT name FROM $table WHERE id = ?", String::class.java, id)

    private fun countAnswers(
        answerTable: String,
        ownerTable: String,
        expoId: String,
    ): Long {
        val column = if (ownerTable == "tb_trainee") "trainee_id" else "standard_participant_id"
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM $answerTable a JOIN $ownerTable p ON p.id = a.$column WHERE p.expo_id = ?",
            Long::class.java,
            expoId,
        )!!
    }

    private fun trainee(expoId: String) =
        Trainee(
            expoId = expoId,
            name = "연수자",
            phoneNumber = "010" + (10_000_000..99_999_999).random(),
            trainingId = "T-" + (1000..9999).random(),
            personalInformationStatus = true,
            applicationType = ApplicationType.PRE,
            applicationDate = LocalDateTime.of(2026, 10, 1, 10, 0),
        )

    private fun participant(expoId: String) =
        StandardParticipant(
            expoId = expoId,
            name = "참가자",
            phoneNumber = "010" + (10_000_000..99_999_999).random(),
            personalInformationStatus = true,
            applicationType = ApplicationType.PRE,
            applicationDate = LocalDateTime.of(2026, 10, 1, 10, 0),
        )

    private companion object {
        const val BLOCK_CHECK_MILLIS = 500L
    }
}
