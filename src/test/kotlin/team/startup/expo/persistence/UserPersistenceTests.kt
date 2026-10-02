package team.startup.expo.persistence

import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.annotation.DirtiesContext
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.entity.StandardParticipantParticipation
import team.startup.expo.domain.participation.entity.StandardParticipantSurveyAnswer
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.domain.training.entity.Trainee
import team.startup.expo.domain.training.entity.TraineeParticipation
import team.startup.expo.domain.training.entity.TraineeSurveyAnswer
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.entity.Authority
import team.startup.expo.domain.user.entity.Status
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest(
    classes = [UserPersistenceTestApplication::class],
    properties = [
        "eureka.client.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
    ],
)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserPersistenceTests {
    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @BeforeEach
    fun clearTables() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE tb_trainee_survey_answer, tb_standard_participant_survey_answer, " +
                "tb_trainee_participation, tb_standard_participant_participation, " +
                "tb_trainee, tb_standard_participant, tb_admin RESTART IDENTITY",
        )
    }

    @Test
    @Transactional
    fun `Admin은 PENDING으로 저장되고 accept하면 ACCEPTED와 ROLE_ADMIN이 된다`() {
        val admin = admin()
        entityManager.persist(admin)
        entityManager.flush()
        entityManager.clear()

        val pending = entityManager.find(Admin::class.java, admin.id)
        pending.status shouldBe Status.PENDING
        pending.authority shouldBe Authority.ROLE_STANDARD

        pending.accept()
        entityManager.flush()
        entityManager.clear()

        val accepted = entityManager.find(Admin::class.java, admin.id)
        accepted.status shouldBe Status.ACCEPTED
        accepted.authority shouldBe Authority.ROLE_ADMIN
    }

    @Test
    fun `Admin의 nickname email phoneNumber 중복을 DB가 막는다`() {
        persistAndFlush(admin())

        shouldViolateConstraint {
            persistAndFlush(admin(nickname = "other", phoneNumber = "01099999999"))
        }
        shouldViolateConstraint {
            persistAndFlush(admin(email = "other@gsm.hs.kr", phoneNumber = "01099999999"))
        }
        shouldViolateConstraint {
            persistAndFlush(admin(nickname = "other", email = "other@gsm.hs.kr"))
        }
    }

    @Test
    @Transactional
    fun `Trainee와 StandardParticipant의 모든 필드를 저장하고 JSON을 보존해 읽는다`() {
        val trainee = trainee()
        val participant = participant()
        entityManager.persist(trainee)
        entityManager.persist(participant)
        entityManager.flush()
        entityManager.clear()

        val reloadedTrainee = entityManager.find(Trainee::class.java, trainee.id)
        reloadedTrainee.expoId shouldBe EXPO_ID
        reloadedTrainee.trainingId shouldBe "T-0001"
        reloadedTrainee.applicationType shouldBe ApplicationType.PRE
        reloadedTrainee.informationJson?.contains("school") shouldBe true

        val reloadedParticipant = entityManager.find(StandardParticipant::class.java, participant.id)
        reloadedParticipant.smsTryTime shouldBe 0
        reloadedParticipant.applicationType shouldBe ApplicationType.FIELD
    }

    @Test
    fun `같은 박람회에서 같은 전화번호의 중복 신청을 DB가 막는다`() {
        persistAndFlush(trainee())
        persistAndFlush(participant())

        shouldViolateConstraint {
            persistAndFlush(trainee(trainingId = "T-0002"))
        }
        shouldViolateConstraint {
            persistAndFlush(participant())
        }
    }

    @Test
    @Transactional
    fun `다른 박람회에서는 같은 전화번호로 신청할 수 있다`() {
        entityManager.persist(trainee())
        entityManager.persist(trainee(expoId = OTHER_EXPO_ID, trainingId = "T-0002"))
        entityManager.flush()

        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_trainee", Long::class.java) shouldBe 2L
    }

    @Test
    @Transactional
    fun `plusSmsTryTime은 smsTryTime을 증가시킨다`() {
        val participant = participant()
        entityManager.persist(participant)
        participant.plusSmsTryTime()
        entityManager.flush()
        entityManager.clear()

        entityManager.find(StandardParticipant::class.java, participant.id).smsTryTime shouldBe 1
    }

    @Test
    fun `출석은 박람회 일자 신청자 조합당 한 번만 기록된다`() {
        val trainee = trainee()
        val participant = participant()
        persistAndFlush(trainee)
        persistAndFlush(participant)
        persistAndFlush(traineeParticipation(trainee))
        persistAndFlush(participantParticipation(participant))

        shouldViolateConstraint {
            persistAndFlush(traineeParticipation(trainee))
        }
        shouldViolateConstraint {
            persistAndFlush(participantParticipation(participant))
        }
    }

    @Test
    fun `설문 답변은 JSON을 보존하고 같은 설문의 중복 제출을 DB가 막는다`() {
        val trainee = trainee()
        val participant = participant()
        persistAndFlush(trainee)
        persistAndFlush(participant)
        persistAndFlush(
            TraineeSurveyAnswer(surveyId = SURVEY_ID, trainee = trainee, answerJson = ANSWER_JSON, personalInformationStatus = true),
        )
        persistAndFlush(
            StandardParticipantSurveyAnswer(
                surveyId = SURVEY_ID,
                standardParticipant = participant,
                answerJson = ANSWER_JSON,
                personalInformationStatus = true,
            ),
        )

        val stored = jdbcTemplate.queryForObject("SELECT answer_json ->> '1' FROM tb_trainee_survey_answer", String::class.java)
        stored shouldBe "만족"

        shouldViolateConstraint {
            persistAndFlush(
                TraineeSurveyAnswer(surveyId = SURVEY_ID, trainee = trainee, answerJson = ANSWER_JSON, personalInformationStatus = false),
            )
        }
        shouldViolateConstraint {
            persistAndFlush(
                StandardParticipantSurveyAnswer(
                    surveyId = SURVEY_ID,
                    standardParticipant = participant,
                    answerJson = ANSWER_JSON,
                    personalInformationStatus = false,
                ),
            )
        }
    }

    @Test
    fun `신청자를 삭제하면 출석과 설문 답변이 함께 삭제된다`() {
        val trainee = trainee()
        persistAndFlush(trainee)
        persistAndFlush(traineeParticipation(trainee))
        persistAndFlush(
            TraineeSurveyAnswer(surveyId = SURVEY_ID, trainee = trainee, answerJson = ANSWER_JSON, personalInformationStatus = true),
        )

        jdbcTemplate.update("DELETE FROM tb_trainee WHERE id = ?", trainee.id)

        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_trainee_participation", Long::class.java) shouldBe 0L
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_trainee_survey_answer", Long::class.java) shouldBe 0L
    }

    private fun persistAndFlush(entity: Any) {
        // 제약 위반 뒤에도 다음 단언이 쓸 수 있도록 매 호출을 독립 트랜잭션으로 실행한다
        transactionTemplate.executeWithoutResult { entityManager.persist(entity) }
    }

    private fun shouldViolateConstraint(block: () -> Unit) {
        val thrown = assertThrows(Exception::class.java) { block() }
        generateSequence<Throwable>(thrown) { it.cause }
            .any { it is ConstraintViolationException || it is DataIntegrityViolationException } shouldBe true
    }

    companion object {
        private const val EXPO_ID = "0199aaaa-0000-7000-8000-000000000001"
        private const val OTHER_EXPO_ID = "0199aaaa-0000-7000-8000-000000000002"
        private const val SURVEY_ID = "5d1c7f4e-0000-4000-8000-000000000001"
        private const val ANSWER_JSON = """{"1":"만족","2":["a","b"]}"""
        private val ENTRY = LocalDateTime.of(2026, 9, 22, 10, 0)
        private val DAY = LocalDate.of(2026, 9, 22)

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")

        private fun admin(
            nickname: String = "admin1",
            email: String = "admin@gsm.hs.kr",
            phoneNumber: String = "01012341234",
        ) = Admin(
            name = "관리자",
            nickname = nickname,
            email = email,
            password = "encoded",
            phoneNumber = phoneNumber,
        )

        private fun trainee(
            expoId: String = EXPO_ID,
            trainingId: String = "T-0001",
        ) = Trainee(
            expoId = expoId,
            name = "연수자",
            phoneNumber = "01011112222",
            trainingId = trainingId,
            informationJson = """{"school":"광주소프트웨어마이스터고"}""",
            personalInformationStatus = true,
            applicationType = ApplicationType.PRE,
            applicationDate = ENTRY,
        )

        private fun participant() =
            StandardParticipant(
                expoId = EXPO_ID,
                name = "참가자",
                phoneNumber = "01033334444",
                personalInformationStatus = true,
                applicationType = ApplicationType.FIELD,
                applicationDate = ENTRY,
            )

        private fun traineeParticipation(trainee: Trainee) =
            TraineeParticipation(entryTime = ENTRY, attendanceDate = DAY, trainee = trainee, expoId = EXPO_ID)

        private fun participantParticipation(participant: StandardParticipant) =
            StandardParticipantParticipation(entryTime = ENTRY, attendanceDate = DAY, standardParticipant = participant, expoId = EXPO_ID)
    }
}

@TestConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EntityScan("team.startup.expo.domain")
class UserPersistenceTestApplication
