package team.startup.expo.internal

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.participation.entity.Occupation
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.domain.training.entity.Trainee
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.support.IntegrationTestSupport
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EntryRecordApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var standardParticipantRepository: StandardParticipantRepository

    @Autowired
    private lateinit var traineeRepository: TraineeRepository

    private val executor = Executors.newFixedThreadPool(THREADS)
    private var standardId = 0L
    private var traineeId = 0L

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE tb_standard_participant_participation, tb_trainee_participation, tb_trainee, tb_standard_participant RESTART IDENTITY CASCADE",
        )
        standardId =
            standardParticipantRepository
                .save(
                    StandardParticipant(
                        expoId = EXPO,
                        name = "홍길동",
                        phoneNumber = "010-1234-5678",
                        personalInformationStatus = true,
                        applicationType = ApplicationType.PRE,
                        applicationDate = LocalDateTime.of(2026, 10, 1, 10, 0),
                        occupation = Occupation.TEACHER,
                        school = "광주소프트웨어마이스터고",
                    ),
                ).id!!
        traineeId =
            traineeRepository
                .save(
                    Trainee(
                        expoId = EXPO,
                        name = "김연수",
                        phoneNumber = "01099998888",
                        trainingId = "T-1",
                        personalInformationStatus = false,
                        applicationType = ApplicationType.PRE,
                        applicationDate = LocalDateTime.of(2026, 10, 1, 10, 0),
                        school = "빛고을초등학교",
                    ),
                ).id!!
    }

    @AfterEach
    fun tearDown() {
        executor.shutdownNow()
    }

    @Test
    fun `일반 참가자의 입장을 기록하고 직업과 소속 학교까지 돌려준다`() {
        enter("STANDARD", "010-1234-5678")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(standardId))
            .andExpect(jsonPath("$.name").value("홍길동"))
            .andExpect(jsonPath("$.phoneNumber").value("010-1234-5678"))
            .andExpect(jsonPath("$.personalInformationStatus").value(true))
            .andExpect(jsonPath("$.participationType").value("STANDARD"))
            .andExpect(jsonPath("$.occupation").value("TEACHER"))
            .andExpect(jsonPath("$.school").value("광주소프트웨어마이스터고"))

        val row = jdbcTemplate.queryForMap("SELECT * FROM tb_standard_participant_participation")
        row["expo_id"] shouldBe EXPO
        row["standard_participant_id"] shouldBe standardId
        row["attendance_date"].toString() shouldBe LocalDate.now(SEOUL).toString()
    }

    @Test
    fun `연수자의 입장을 기록하고 직업 없이 소속 학교만 돌려준다`() {
        enter("TRAINEE", "01099998888")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(traineeId))
            .andExpect(jsonPath("$.participationType").value("TRAINEE"))
            .andExpect(jsonPath("$.personalInformationStatus").value(false))
            .andExpect(jsonPath("$.occupation").doesNotExist())
            .andExpect(jsonPath("$.school").value("빛고을초등학교"))

        count("tb_trainee_participation") shouldBe 1
        count("tb_standard_participant_participation") shouldBe 0
    }

    @Test
    fun `표기가 달라도 같은 번호로 찾는다`() {
        enter("STANDARD", "01012345678").andExpect(status().isOk).andExpect(jsonPath("$.id").value(standardId))
    }

    @Test
    fun `같은 날 두 번째 입장은 409이고 기록은 하나만 남는다`() {
        enter("STANDARD", "010-1234-5678").andExpect(status().isOk)

        enter("STANDARD", "010-1234-5678").andExpect(status().isConflict)
        enter("TRAINEE", "01099998888").andExpect(status().isOk)
        enter("TRAINEE", "01099998888").andExpect(status().isConflict)

        count("tb_standard_participant_participation") shouldBe 1
        count("tb_trainee_participation") shouldBe 1
    }

    @Test
    fun `날이 바뀌면 다시 입장할 수 있다`() {
        jdbcTemplate.update(
            "INSERT INTO tb_standard_participant_participation (entry_time, attendance_date, standard_participant_id, expo_id) VALUES (?, ?, ?, ?)",
            LocalDateTime.now(SEOUL).minusDays(1),
            LocalDate.now(SEOUL).minusDays(1),
            standardId,
            EXPO,
        )

        enter("STANDARD", "010-1234-5678").andExpect(status().isOk)

        count("tb_standard_participant_participation") shouldBe 2
    }

    @Test
    fun `참가자가 없거나 다른 박람회거나 다른 구분이면 404이고 기록하지 않는다`() {
        enter("STANDARD", "01000000000").andExpect(status().isNotFound)
        enter("STANDARD", "010-1234-5678", expoId = OTHER_EXPO).andExpect(status().isNotFound)
        // 일반 참가자의 번호를 연수자로 보내면 연수자 테이블만 보므로 없다
        enter("TRAINEE", "010-1234-5678").andExpect(status().isNotFound)

        count("tb_standard_participant_participation") shouldBe 0
        count("tb_trainee_participation") shouldBe 0
    }

    @Test
    fun `같은 번호의 일반 참가자와 연수자는 서로의 입장에 영향을 주지 않는다`() {
        traineeRepository.save(
            Trainee(
                expoId = EXPO,
                name = "같은번호",
                phoneNumber = "010-1234-5678",
                trainingId = "T-2",
                personalInformationStatus = true,
                applicationType = ApplicationType.PRE,
                applicationDate = LocalDateTime.of(2026, 10, 1, 10, 0),
            ),
        )

        enter("STANDARD", "010-1234-5678").andExpect(status().isOk).andExpect(jsonPath("$.name").value("홍길동"))
        enter("TRAINEE", "010-1234-5678").andExpect(status().isOk).andExpect(jsonPath("$.name").value("같은번호"))
    }

    @Test
    fun `같은 참가자의 동시 스캔은 한 번만 기록하고 나머지는 409이다`() {
        val start = CountDownLatch(1)
        val futures =
            List(THREADS) {
                executor.submit<Int> {
                    start.await()
                    enter("STANDARD", "010-1234-5678").andReturn().response.status
                }
            }
        start.countDown()
        val statuses = futures.map { it.get(30, TimeUnit.SECONDS) }

        statuses.count { it == 200 } shouldBe 1
        statuses.count { it == 409 } shouldBe THREADS - 1
        count("tb_standard_participant_participation") shouldBe 1
    }

    @Test
    fun `잘못된 요청은 400이다`() {
        enter("STANDARD", "전화번호아님").andExpect(status().isBadRequest)
        enter("VISITOR", "01012345678").andExpect(status().isBadRequest)
        mockMvc
            .perform(
                post(
                    "/internal/entries",
                ).header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON).content("""{"expoId":"$EXPO"}"""),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `토큰이 없거나 틀리거나 관리자 토큰만 있으면 401이고 기록하지 않는다`() {
        val body = """{"expoId":"$EXPO","participationType":"STANDARD","phoneNumber":"010-1234-5678"}"""
        mockMvc.perform(post("/internal/entries").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized)
        mockMvc
            .perform(post("/internal/entries").header("X-Internal-Token", "wrong").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)
        mockMvc
            .perform(post("/internal/entries").header("Authorization", bearerOf(1L)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)

        count("tb_standard_participant_participation") shouldBe 0
    }

    private fun enter(
        type: String,
        phone: String,
        expoId: String = EXPO,
    ): ResultActions =
        mockMvc.perform(
            post("/internal/entries")
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"expoId":"$expoId","participationType":"$type","phoneNumber":"$phone"}"""),
        )

    private fun count(table: String) = jdbcTemplate.queryForObject("SELECT count(*) FROM $table", Long::class.java)

    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-0000000000b1"
        const val OTHER_EXPO = "0199aaaa-0000-7000-8000-0000000000b2"
        const val THREADS = 8
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
