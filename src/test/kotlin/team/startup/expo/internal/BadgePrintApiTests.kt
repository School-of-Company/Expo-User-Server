package team.startup.expo.internal

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.participation.entity.Occupation
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.BadgePrintService
import team.startup.expo.domain.participation.service.PurgeExpoDataService
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.domain.training.entity.Trainee
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.support.IntegrationTestSupport
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BadgePrintApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    @Autowired
    private lateinit var standardParticipantRepository: StandardParticipantRepository

    @Autowired
    private lateinit var traineeRepository: TraineeRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var badgePrintService: BadgePrintService

    @Autowired
    private lateinit var purgeExpoDataService: PurgeExpoDataService

    private val executor = Executors.newFixedThreadPool(THREADS)
    private var adminId = 0L
    private var phoneSeq = 0

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE tb_badge_print_attempt, tb_badge_print_job, tb_badge_print_station, tb_standard_participant_participation, " +
                "tb_trainee_participation, tb_trainee, tb_standard_participant, tb_expo_deletion RESTART IDENTITY CASCADE",
        )
        clearAdmins()
        adminId =
            adminRepository
                .save(
                    Admin(name = "관리자", nickname = "badge", email = "badge@gsm.hs.kr", password = "encoded", phoneNumber = "01000000000")
                        .apply { accept() },
                ).id!!
    }

    @AfterEach
    fun tearDown() {
        executor.shutdownNow()
    }

    @Test
    fun `교사 입장은 작업을 저장하고 같은 날 다른 회차 입장은 DUPLICATE이며 대상이 아니면 null이다`() {
        val teacher = standard(Occupation.TEACHER)
        val preTeacher = standard(Occupation.PRE_SERVICE_TEACHER)
        val parent = standard(Occupation.PARENT)

        enter(teacher).andExpect(status().isOk).andExpect(jsonPath("$.badgePrint").value("QUEUED"))
        enter(teacher, sessionId = 7).andExpect(status().isOk).andExpect(jsonPath("$.badgePrint").value("DUPLICATE"))
        enter(preTeacher).andExpect(status().isOk).andExpect(jsonPath("$.badgePrint").value("QUEUED"))
        enter(parent).andExpect(status().isOk).andExpect(jsonPath("$.badgePrint").doesNotExist())
        // 같은 회차 재입장(409)은 결과를 주지 않고 작업도 만들지 않는다
        enter(teacher, sessionId = 7).andExpect(status().isConflict).andExpect(jsonPath("$.badgePrint").doesNotExist())

        jdbcTemplate.queryForList("SELECT person_key FROM tb_badge_print_job ORDER BY id", String::class.java) shouldBe
            listOf("S:${teacher.id}", "S:${preTeacher.id}")
        jdbcTemplate.queryForMap("SELECT name, school, status FROM tb_badge_print_job WHERE id = 1") shouldBe
            mapOf("name" to "참가자", "school" to "광주소프트웨어마이스터고", "status" to "WAITING")
    }

    @Test
    fun `연결된 연수자는 참가자와 같은 사람이고 연결 없는 연수자는 따로 출력한다`() {
        val teacher = standard(Occupation.TEACHER)
        val linked = trainee(standardParticipantId = teacher.id)
        val alone = trainee()

        enter(teacher).andExpect(jsonPath("$.badgePrint").value("QUEUED"))
        enterTrainee(linked).andExpect(status().isOk).andExpect(jsonPath("$.badgePrint").value("DUPLICATE"))
        enterTrainee(alone).andExpect(status().isOk).andExpect(jsonPath("$.badgePrint").value("QUEUED"))

        jdbcTemplate.queryForList("SELECT person_key FROM tb_badge_print_job ORDER BY id", String::class.java) shouldBe
            listOf("S:${teacher.id}", "T:${alone.id}")
    }

    @Test
    fun `연결된 연수자가 먼저 들어오면 참가자 입장이 DUPLICATE이다`() {
        val teacher = standard(Occupation.GENERAL)
        val linked = trainee(standardParticipantId = teacher.id)

        enterTrainee(linked).andExpect(jsonPath("$.badgePrint").value("QUEUED"))
        // 직업이 대상이 아닌 참가자는 연결된 연수자와 상관없이 대상이 아니다
        enter(teacher).andExpect(status().isOk).andExpect(jsonPath("$.badgePrint").doesNotExist())

        count("tb_badge_print_job") shouldBe 1
    }

    @Test
    fun `다음 날 입장은 새 작업을 만든다`() {
        val teacher = standard(Occupation.TEACHER)
        enter(teacher).andExpect(jsonPath("$.badgePrint").value("QUEUED"))
        jdbcTemplate.update("UPDATE tb_badge_print_job SET attendance_date = attendance_date - 1")
        jdbcTemplate.update("UPDATE tb_standard_participant_participation SET attendance_date = attendance_date - 1")

        enter(teacher).andExpect(jsonPath("$.badgePrint").value("QUEUED"))

        count("tb_badge_print_job") shouldBe 2
    }

    @Test
    fun `같은 사람의 다른 회차 동시 입장도 작업은 하나다`() {
        val teacher = standard(Occupation.TEACHER)
        val start = CountDownLatch(1)
        val results =
            (1..THREADS)
                .map { session ->
                    executor.submit(
                        Callable {
                            start.await()
                            enter(
                                teacher,
                                sessionId = session.toLong(),
                            ).andReturn().response.let { jsonMapper.readTree(it.contentAsString) }
                        },
                    )
                }.also { start.countDown() }
                .map { it.get(30, TimeUnit.SECONDS)["badgePrint"].asString() }

        results.count { it == "QUEUED" } shouldBe 1
        results.count { it == "DUPLICATE" } shouldBe THREADS - 1
        count("tb_badge_print_job") shouldBe 1
    }

    @Test
    fun `작업 저장이 실패하면 입장도 되돌린다`() {
        val teacher = standard(Occupation.TEACHER)
        jdbcTemplate.execute(
            "CREATE FUNCTION fail_badge_job() RETURNS trigger AS \$\$ BEGIN RAISE EXCEPTION 'boom'; END; \$\$ LANGUAGE plpgsql",
        )
        jdbcTemplate.execute(
            "CREATE TRIGGER trg_fail_badge_job BEFORE INSERT ON tb_badge_print_job FOR EACH ROW EXECUTE FUNCTION fail_badge_job()",
        )
        try {
            enter(teacher).andExpect(status().isInternalServerError)
        } finally {
            jdbcTemplate.execute("DROP TRIGGER trg_fail_badge_job ON tb_badge_print_job")
            jdbcTemplate.execute("DROP FUNCTION fail_badge_job()")
        }

        count("tb_standard_participant_participation") shouldBe 0
        count("tb_badge_print_job") shouldBe 0
        enter(teacher).andExpect(status().isOk).andExpect(jsonPath("$.badgePrint").value("QUEUED"))
    }

    @Test
    fun `출력대가 없어도 작업은 대기로 남고 출력대가 생기면 오래된 순서로 준다`() {
        val first = standard(Occupation.TEACHER)
        val second = standard(Occupation.TEACHER)
        enter(first)
        enter(second)
        // 지난 날짜 작업은 자동으로 출력하지 않는다
        jdbcTemplate.update(
            "INSERT INTO tb_badge_print_job (expo_id, person_key, attendance_date, participation_type, subject_id, name, status, " +
                "created_at, updated_at) VALUES (?, 'S:999', ?, 'STANDARD', 999, '어제', 'WAITING', now(), now())",
            EXPO,
            today().minusDays(1),
        )

        val station = createStation()
        val claimed1 = claim(station).andExpect(status().isOk).json()
        result(station, claimed1, "SUBMITTED").andExpect(status().isNoContent)
        val claimed2 = claim(station).andExpect(status().isOk).json()
        result(station, claimed2, "SUBMITTED").andExpect(status().isNoContent)
        claim(station).andExpect(status().isNoContent)

        claimed1["name"].asString() shouldBe "참가자"
        claimed1["school"].asString() shouldBe "광주소프트웨어마이스터고"
        listOf(claimed1["jobId"].asLong(), claimed2["jobId"].asLong()) shouldBe listOf(1L, 2L)
        statusOf(1) shouldBe "SUBMITTED"
        jdbcTemplate.queryForObject("SELECT status FROM tb_badge_print_job WHERE name = '어제'", String::class.java) shouldBe "WAITING"
    }

    @Test
    fun `출력대 다섯 대가 동시에 claim해도 한 작업은 한 번만 나간다`() {
        repeat(JOBS) { enter(standard(Occupation.TEACHER)) }
        val stations = (1..5).map { createStation() }
        val start = CountDownLatch(1)
        val claimedIds =
            stations
                .map { station ->
                    executor.submit(
                        Callable {
                            start.await()
                            val ids = mutableListOf<Long>()
                            while (true) {
                                val response = claim(station).andReturn().response
                                if (response.status == 204) break
                                val claimed = jsonMapper.readTree(response.contentAsString)
                                ids += claimed["jobId"].asLong()
                                result(station, claimed, "SUBMITTED").andExpect(status().isNoContent)
                            }
                            ids
                        },
                    )
                }.also { start.countDown() }
                .flatMap { it.get(60, TimeUnit.SECONDS) }

        claimedIds.sorted() shouldBe (1L..JOBS).toList()
        jdbcTemplate.queryForList("SELECT DISTINCT status FROM tb_badge_print_job", String::class.java) shouldBe listOf("SUBMITTED")
        count("tb_badge_print_attempt") shouldBe JOBS.toLong()
    }

    @Test
    fun `한 출력대의 동시 claim은 활성 작업을 하나만 남기고 같은 requestId 재시도는 같은 점유를 준다`() {
        repeat(3) { enter(standard(Occupation.TEACHER)) }
        val station = createStation()
        val requestId = UUID.randomUUID()
        val start = CountDownLatch(1)
        val responses =
            (1..THREADS)
                .map {
                    executor.submit(
                        Callable {
                            start.await()
                            claim(station, requestId).andReturn().response.let { jsonMapper.readTree(it.contentAsString) }
                        },
                    )
                }.also { start.countDown() }
                .map { it.get(30, TimeUnit.SECONDS) }

        responses.map { it["jobId"].asLong() }.toSet() shouldBe setOf(1L)
        responses.map { it["claimToken"].asString() }.toSet() shouldHaveSize 1
        count("tb_badge_print_attempt") shouldBe 1

        // 다른 requestId의 동시 claim은 줄을 서서, 앞 요청이 결과 없이 남긴 점유를 확인 필요로 닫고 다음 작업을 받는다.
        // 그래서 어느 작업도 두 번 점유되지 않고 출력대의 활성 작업은 하나를 넘지 않는다
        val start2 = CountDownLatch(1)
        (1..THREADS)
            .map {
                executor.submit(
                    Callable {
                        start2.await()
                        claim(station).andReturn().response.status
                    },
                )
            }.also { start2.countDown() }
            .forEach { it.get(30, TimeUnit.SECONDS) }
        jdbcTemplate.queryForList("SELECT status FROM tb_badge_print_job ORDER BY id", String::class.java) shouldBe
            List(3) { "NEEDS_CHECK" }
        jdbcTemplate.queryForList("SELECT job_id FROM tb_badge_print_attempt ORDER BY job_id", Long::class.java) shouldBe
            listOf(1L, 2L, 3L)
        jdbcTemplate.queryForList("SELECT DISTINCT outcome FROM tb_badge_print_attempt", String::class.java) shouldBe listOf("ABANDONED")
    }

    @Test
    fun `새 세션이 열리면 이전 세션은 409이고 이전 점유는 확인 필요가 된다`() {
        enter(standard(Occupation.TEACHER))
        enter(standard(Occupation.TEACHER))
        val station = createStation()
        val claimed = claim(station).andExpect(status().isOk).json()

        val newSession =
            mockMvc
                .perform(post("$BASE/stations/${station.id}/sessions").header("Authorization", bearerOf(adminId)))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .let { UUID.fromString(jsonMapper.readTree(it.contentAsString)["sessionId"].asString()) }

        claim(station).andExpect(status().isConflict)
        result(station, claimed, "SUBMITTED").andExpect(status().isConflict)
        statusOf(1) shouldBe "NEEDS_CHECK"
        attemptOutcomes(1) shouldBe listOf("TAKEN_OVER")

        // 새 세션은 다음 작업을 받는다. 확인 필요 작업은 자동으로 다시 주지 않는다
        val next = claim(station.copy(sessionId = newSession)).andExpect(status().isOk).json()
        next["jobId"].asLong() shouldBe 2L
    }

    @Test
    fun `점유 후 30초 안에 결과가 없으면 claim 때 확인 필요가 되고 늦은 결과는 409이다`() {
        enter(standard(Occupation.TEACHER))
        enter(standard(Occupation.TEACHER))
        val station = createStation()
        val other = createStation()
        val claimed = claim(station).andExpect(status().isOk).json()
        jdbcTemplate.update("UPDATE tb_badge_print_job SET claimed_at = claimed_at - interval '31 seconds' WHERE id = 1")

        claim(other).andExpect(status().isOk).andExpect(jsonPath("$.jobId").value(2))

        statusOf(1) shouldBe "NEEDS_CHECK"
        attemptOutcomes(1) shouldBe listOf("EXPIRED")
        result(station, claimed, "SUBMITTED").andExpect(status().isConflict)
        statusOf(1) shouldBe "NEEDS_CHECK"
    }

    @Test
    fun `출력대가 모두 멈춰도 주기 작업이 결과 없는 점유를 확인 필요로 닫는다`() {
        enter(standard(Occupation.TEACHER))
        enter(standard(Occupation.TEACHER))
        val station = createStation()
        claim(station).andExpect(status().isOk)
        jdbcTemplate.update("UPDATE tb_badge_print_job SET claimed_at = claimed_at - interval '29 seconds' WHERE id = 1")
        badgePrintService.expireClaims() shouldBe 0
        jdbcTemplate.update("UPDATE tb_badge_print_job SET claimed_at = claimed_at - interval '2 seconds' WHERE id = 1")

        badgePrintService.expireClaims() shouldBe 1

        statusOf(1) shouldBe "NEEDS_CHECK"
        statusOf(2) shouldBe "WAITING"
        getJobs().andExpect(jsonPath("$.jobs[0].jobId").value(1)).andExpect(jsonPath("$.jobs[0].status").value("NEEDS_CHECK"))
    }

    @Test
    fun `인쇄 전 실패는 2초 5초 15초 뒤 다시 대기하고 네 번째 실패에서 FAILED이다`() {
        enter(standard(Occupation.TEACHER))
        val station = createStation()
        val delays = mutableListOf<Long>()
        repeat(3) { index ->
            val claimed = claim(station).andExpect(status().isOk).json()
            result(station, claimed, "FAILED_BEFORE_PRINT", reason = "printer offline").andExpect(status().isNoContent)
            val row = jdbcTemplate.queryForMap("SELECT status, failures, next_attempt_at, updated_at FROM tb_badge_print_job WHERE id = 1")
            row["status"] shouldBe "WAITING"
            row["failures"] shouldBe index + 1
            delays +=
                ChronoUnit.SECONDS.between(
                    (row["updated_at"] as java.sql.Timestamp).toLocalDateTime(),
                    (row["next_attempt_at"] as java.sql.Timestamp).toLocalDateTime(),
                )
            // 재시도 시각 전에는 주지 않는다
            claim(station).andExpect(status().isNoContent)
            jdbcTemplate.update("UPDATE tb_badge_print_job SET next_attempt_at = next_attempt_at - interval '1 minute'")
        }
        val last = claim(station).andExpect(status().isOk).json()
        result(station, last, "FAILED_BEFORE_PRINT").andExpect(status().isNoContent)

        delays shouldBe listOf(2L, 5L, 15L)
        jdbcTemplate.queryForMap("SELECT status, failures FROM tb_badge_print_job WHERE id = 1") shouldBe
            mapOf("status" to "FAILED", "failures" to 4)
        attemptOutcomes(1) shouldBe List(4) { "FAILED_BEFORE_PRINT" }
        claim(station).andExpect(status().isNoContent)
        mockMvc
            .perform(get("$BASE/jobs/1/attempts").header("Authorization", bearerOf(adminId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.attempts.length()").value(4))
            .andExpect(jsonPath("$.attempts[0].reason").value("printer offline"))
            .andExpect(jsonPath("$.attempts[0].stationId").value(station.id.toString()))
    }

    @Test
    fun `결과 재전송은 같은 결과면 204이고 다른 결과면 409이며 다른 토큰은 409이다`() {
        enter(standard(Occupation.TEACHER))
        val station = createStation()
        val claimed = claim(station).andExpect(status().isOk).json()

        result(station, claimed, "SUBMITTED").andExpect(status().isNoContent)
        result(station, claimed, "SUBMITTED").andExpect(status().isNoContent)
        result(station, claimed, "FAILED_BEFORE_PRINT").andExpect(status().isConflict)
        result(station, claimed, "SUBMITTED", token = UUID.randomUUID()).andExpect(status().isConflict)
        result(station, claimed, "SUBMITTED", jobId = 99).andExpect(status().isConflict)

        statusOf(1) shouldBe "SUBMITTED"
        attemptOutcomes(1) shouldBe listOf("SUBMITTED")
    }

    @Test
    fun `일시정지한 출력대는 새 작업을 받지 않고 재개하면 받는다`() {
        enter(standard(Occupation.TEACHER))
        val station = createStation()
        pause(station, true).andExpect(status().isNoContent)
        claim(station).andExpect(status().isNoContent)
        mockMvc
            .perform(get("$BASE/stations").header("Authorization", bearerOf(adminId)))
            .andExpect(jsonPath("$.stations[0].paused").value(true))
            .andExpect(jsonPath("$.stations[0].label").value("출력대 1"))

        pause(station, false).andExpect(status().isNoContent)
        claim(station).andExpect(status().isOk).andExpect(jsonPath("$.jobId").value(1))
    }

    @Test
    fun `REQUEUE는 원본을 가리키는 새 작업을 한 번만 만들고 같은 키 재요청은 204이다`() {
        enter(standard(Occupation.TEACHER))
        val station = createStation()
        claim(station).andExpect(status().isOk)
        badgeExpire()
        val key = UUID.randomUUID()

        resolve(1, "REQUEUE", key).andExpect(status().isNoContent)
        resolve(1, "REQUEUE", key).andExpect(status().isNoContent)
        resolve(1, "REQUEUE", UUID.randomUUID()).andExpect(status().isConflict)
        resolve(1, "DONE", UUID.randomUUID()).andExpect(status().isConflict)

        jdbcTemplate.queryForMap("SELECT status, resolved_by FROM tb_badge_print_job WHERE id = 1") shouldBe
            mapOf("status" to "REQUEUED", "resolved_by" to adminId)
        jdbcTemplate.queryForMap(
            "SELECT original_job_id, request_key, status, attendance_date FROM tb_badge_print_job WHERE id = 2",
        ) shouldBe
            mapOf("original_job_id" to 1L, "request_key" to key, "status" to "WAITING", "attendance_date" to java.sql.Date.valueOf(today()))
        val reprint = claim(station).andExpect(status().isOk).json()
        reprint["jobId"].asLong() shouldBe 2L
        reprint["name"].asString() shouldBe "참가자"
    }

    @Test
    fun `동시 REQUEUE도 새 작업은 하나다`() {
        enter(standard(Occupation.TEACHER))
        claim(createStation()).andExpect(status().isOk)
        badgeExpire()
        val start = CountDownLatch(1)
        val statuses =
            (1..THREADS)
                .map {
                    executor.submit(
                        Callable {
                            start.await()
                            resolve(1, "REQUEUE", UUID.randomUUID()).andReturn().response.status
                        },
                    )
                }.also { start.countDown() }
                .map { it.get(30, TimeUnit.SECONDS) }

        statuses.count { it == 204 } shouldBe 1
        statuses.count { it == 409 } shouldBe THREADS - 1
        count("tb_badge_print_job") shouldBe 2
    }

    @Test
    fun `DONE은 확인 필요와 실패만 닫고 다시 보내도 204이다`() {
        enter(standard(Occupation.TEACHER))
        enter(standard(Occupation.TEACHER))
        resolve(1, "DONE", UUID.randomUUID()).andExpect(status().isConflict)
        val station = createStation()
        claim(station).andExpect(status().isOk)
        badgeExpire()

        resolve(1, "DONE", UUID.randomUUID()).andExpect(status().isNoContent)
        resolve(1, "DONE", UUID.randomUUID()).andExpect(status().isNoContent)

        statusOf(1) shouldBe "DONE"
        getJobs().andExpect(jsonPath("$.jobs").isEmpty)
        mockMvc
            .perform(get("$BASE/jobs").param("status", "DONE,WAITING").header("Authorization", bearerOf(adminId)))
            .andExpect(jsonPath("$.jobs[*].status").value(org.hamcrest.Matchers.contains("DONE", "WAITING")))
    }

    @Test
    fun `박람회 삭제는 작업과 출력대와 시도 이력을 지운다`() {
        enter(standard(Occupation.TEACHER))
        val station = createStation()
        claim(station).andExpect(status().isOk)
        jdbcTemplate.update(
            "INSERT INTO tb_badge_print_station (id, expo_id, label, session_id, created_at) VALUES (?, ?, '다른', ?, now())",
            UUID.randomUUID(),
            OTHER_EXPO,
            UUID.randomUUID(),
        )

        purgeExpoDataService.execute(EXPO)

        count("tb_badge_print_job") shouldBe 0
        count("tb_badge_print_attempt") shouldBe 0
        jdbcTemplate.queryForList("SELECT expo_id FROM tb_badge_print_station", String::class.java) shouldContainExactly listOf(OTHER_EXPO)
    }

    @Test
    fun `토큰이 없으면 401이고 다른 박람회의 출력대와 작업은 404이다`() {
        enter(standard(Occupation.TEACHER))
        val station = createStation()

        mockMvc.perform(post("$BASE/stations")).andExpect(status().isUnauthorized)
        mockMvc
            .perform(post("$BASE/stations").header("Authorization", bearerOf(adminId + 100)))
            .andExpect(status().isUnauthorized)
        mockMvc
            .perform(
                post("/admin/badge-prints/$OTHER_EXPO/stations/${station.id}/claim")
                    .header("Authorization", bearerOf(adminId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"sessionId":"${station.sessionId}"}"""),
            ).andExpect(status().isNotFound)
        mockMvc
            .perform(get("/admin/badge-prints/$OTHER_EXPO/jobs/1/attempts").header("Authorization", bearerOf(adminId)))
            .andExpect(status().isNotFound)
        mockMvc
            .perform(
                post("/admin/badge-prints/$OTHER_EXPO/jobs/1/resolve")
                    .header("Authorization", bearerOf(adminId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"action":"DONE","requestKey":"${UUID.randomUUID()}"}"""),
            ).andExpect(status().isNotFound)
        statusOf(1) shouldBe "WAITING"
    }

    @Test
    fun `승인 전 관리자는 403이다`() {
        val pending =
            adminRepository
                .save(
                    Admin(
                        name = "대기",
                        nickname = "pending",
                        email = "pending@gsm.hs.kr",
                        password = "encoded",
                        phoneNumber = "01000000001",
                    ),
                ).id!!

        mockMvc.perform(post("$BASE/stations").header("Authorization", bearerOf(pending))).andExpect(status().isForbidden)
        count("tb_badge_print_station") shouldBe 0
    }

    @Test
    fun `출력대 이름을 주면 그 이름을 쓰고 없으면 등록 순서로 붙인다`() {
        mockMvc
            .perform(
                post("$BASE/stations")
                    .header("Authorization", bearerOf(adminId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"label":"입구 왼쪽"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.label").value("입구 왼쪽"))
        createStation()

        jdbcTemplate.queryForList("SELECT label FROM tb_badge_print_station ORDER BY created_at", String::class.java) shouldBe
            listOf("입구 왼쪽", "출력대 2")
        jdbcTemplate
            .queryForList("SELECT label FROM tb_badge_print_station WHERE expo_id = ?", String::class.java, OTHER_EXPO)
            .shouldBeEmpty()
    }

    private data class Station(
        val id: UUID,
        val sessionId: UUID,
    )

    private fun standard(occupation: Occupation): StandardParticipant =
        standardParticipantRepository.save(
            StandardParticipant(
                expoId = EXPO,
                name = "참가자",
                phoneNumber = nextPhone(),
                personalInformationStatus = true,
                applicationType = ApplicationType.PRE,
                applicationDate = LocalDateTime.of(2026, 10, 1, 10, 0),
                occupation = occupation,
                school = "광주소프트웨어마이스터고",
            ),
        )

    private fun trainee(standardParticipantId: Long? = null): Trainee =
        traineeRepository.save(
            Trainee(
                expoId = EXPO,
                name = "연수자",
                phoneNumber = nextPhone(),
                trainingId = "T-1",
                personalInformationStatus = true,
                applicationType = ApplicationType.PRE,
                applicationDate = LocalDateTime.of(2026, 10, 1, 10, 0),
                school = "빛고을초등학교",
                standardParticipantId = standardParticipantId,
            ),
        )

    private fun nextPhone() = "0105555%04d".format(++phoneSeq)

    private fun enter(
        participant: StandardParticipant,
        sessionId: Long? = null,
    ): ResultActions =
        mockMvc.perform(
            post("/internal/entries")
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"expoId":"$EXPO","participationType":"STANDARD","phoneNumber":"${participant.phoneNumber}"""" +
                        (sessionId?.let { ""","preregisterSessionId":$it""" } ?: "") + "}",
                ),
        )

    private fun enterTrainee(trainee: Trainee): ResultActions =
        mockMvc.perform(
            post("/internal/entries")
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"expoId":"$EXPO","participationType":"TRAINEE","phoneNumber":"${trainee.phoneNumber}"}"""),
        )

    private fun createStation(): Station =
        mockMvc
            .perform(post("$BASE/stations").header("Authorization", bearerOf(adminId)))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .let { jsonMapper.readTree(it.contentAsString) }
            .let { Station(UUID.fromString(it["stationId"].asString()), UUID.fromString(it["sessionId"].asString())) }

    private fun claim(
        station: Station,
        requestId: UUID = UUID.randomUUID(),
    ): ResultActions =
        mockMvc.perform(
            post("$BASE/stations/${station.id}/claim")
                .header("Authorization", bearerOf(adminId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sessionId":"${station.sessionId}","requestId":"$requestId"}"""),
        )

    private fun result(
        station: Station,
        claimed: JsonNode,
        outcome: String,
        token: UUID = UUID.fromString(claimed["claimToken"].asString()),
        jobId: Long = claimed["jobId"].asLong(),
        reason: String? = null,
    ): ResultActions =
        mockMvc.perform(
            post("$BASE/jobs/$jobId/result")
                .header("Authorization", bearerOf(adminId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"sessionId":"${station.sessionId}","claimToken":"$token","outcome":"$outcome"""" +
                        (reason?.let { ""","reason":"$it"""" } ?: "") + "}",
                ),
        )

    private fun pause(
        station: Station,
        paused: Boolean,
    ): ResultActions =
        mockMvc.perform(
            patch("$BASE/stations/${station.id}")
                .header("Authorization", bearerOf(adminId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"paused":$paused}"""),
        )

    private fun resolve(
        jobId: Long,
        action: String,
        key: UUID,
    ): ResultActions =
        mockMvc.perform(
            post("$BASE/jobs/$jobId/resolve")
                .header("Authorization", bearerOf(adminId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"action":"$action","requestKey":"$key"}"""),
        )

    private fun getJobs(): ResultActions = mockMvc.perform(get("$BASE/jobs").header("Authorization", bearerOf(adminId)))

    private fun badgeExpire() {
        jdbcTemplate.update("UPDATE tb_badge_print_job SET claimed_at = claimed_at - interval '31 seconds' WHERE status = 'CLAIMED'")
        badgePrintService.expireClaims()
    }

    private fun ResultActions.json(): JsonNode = jsonMapper.readTree(andReturn().response.contentAsString)

    private fun statusOf(jobId: Long) =
        jdbcTemplate.queryForObject("SELECT status FROM tb_badge_print_job WHERE id = ?", String::class.java, jobId)

    private fun attemptOutcomes(jobId: Long) =
        jdbcTemplate.queryForList(
            "SELECT outcome FROM tb_badge_print_attempt WHERE job_id = ? ORDER BY claimed_at",
            String::class.java,
            jobId,
        )

    private fun count(table: String) = jdbcTemplate.queryForObject("SELECT count(*) FROM $table", Long::class.java)

    private fun today() = LocalDate.now(SEOUL)

    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-0000000000c1"
        const val OTHER_EXPO = "0199aaaa-0000-7000-8000-0000000000c2"
        const val BASE = "/admin/badge-prints/$EXPO"
        const val THREADS = 8
        const val JOBS = 20
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
