package team.startup.expo.query

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.entity.StandardParticipantParticipation
import team.startup.expo.domain.participation.repository.StandardParticipantParticipationRepository
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.domain.training.entity.Trainee
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.support.FakeExpoServer
import team.startup.expo.support.IntegrationTestSupport
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 관리자 한 명과 박람회 두 개(오늘이 기간에 든 A, 지난 B)를 준비한다. Expo 서비스는 가짜 HTTP 서버다. */
abstract class QueryApiTestSupport : IntegrationTestSupport() {
    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var adminRepository: AdminRepository

    @Autowired
    protected lateinit var traineeRepository: TraineeRepository

    @Autowired
    protected lateinit var standardParticipantRepository: StandardParticipantRepository

    @Autowired
    protected lateinit var participationRepository: StandardParticipantParticipationRepository

    @Autowired
    private lateinit var expoCircuitBreaker: CircuitBreaker

    protected var adminId = 0L
    protected var pendingAdminId = 0L

    @BeforeEach
    fun setUpQueryApi() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE tb_standard_participant_participation, tb_trainee, tb_standard_participant, tb_admin RESTART IDENTITY CASCADE",
        )
        FakeExpoServer.reset()
        expoCircuitBreaker.reset()
        FakeExpoServer.register(EXPO_A, TODAY.minusDays(1).toString(), TODAY.plusDays(1).toString())
        FakeExpoServer.register(EXPO_B, TODAY.minusDays(30).toString(), TODAY.minusDays(20).toString())
        adminId = adminRepository.save(admin("admin", "01000000000").apply { accept() }).id!!
        pendingAdminId = adminRepository.save(admin("pending", "01000000001")).id!!
    }

    protected fun saveTrainee(
        expoId: String,
        name: String,
        phoneNumber: String,
        trainingId: String,
        applicationType: ApplicationType = ApplicationType.PRE,
    ) = traineeRepository
        .save(
            Trainee(
                expoId = expoId,
                name = name,
                phoneNumber = phoneNumber,
                trainingId = trainingId,
                informationJson = """{"school":"큰 JSON"}""",
                personalInformationStatus = true,
                applicationType = applicationType,
                applicationDate = LocalDateTime.of(2026, 10, 1, 10, 0),
            ),
        ).id!!

    protected fun saveParticipant(
        expoId: String,
        name: String,
        phoneNumber: String,
        personalInformationStatus: Boolean = true,
    ) = standardParticipantRepository.save(
        StandardParticipant(
            expoId = expoId,
            name = name,
            phoneNumber = phoneNumber,
            personalInformationStatus = personalInformationStatus,
            applicationType = ApplicationType.PRE,
            applicationDate = LocalDateTime.of(2026, 10, 1, 10, 0),
        ),
    )

    protected fun attend(
        participant: StandardParticipant,
        date: LocalDate,
    ) {
        participationRepository.save(
            StandardParticipantParticipation(
                entryTime = date.atTime(10, 0),
                attendanceDate = date,
                standardParticipant = participant,
                expoId = participant.expoId,
            ),
        )
    }

    private fun admin(
        nickname: String,
        phoneNumber: String,
    ) = Admin(
        name = "관리자",
        nickname = nickname,
        email = "$nickname@gsm.hs.kr",
        password = "encoded",
        phoneNumber = phoneNumber,
    )

    protected companion object {
        const val EXPO_A = "0199aaaa-0000-7000-8000-0000000000a1"
        const val EXPO_B = "0199aaaa-0000-7000-8000-0000000000b2"
        val TODAY: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))
    }
}
