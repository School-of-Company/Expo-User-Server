package team.startup.expo.internal

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 이어지지 않은 레거시 연수자의 귀속 확인(연결)과 정정(해제). */
class LegacyTraineeAttributionApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var standardParticipantRepository: StandardParticipantRepository

    @Autowired
    private lateinit var traineeRepository: TraineeRepository

    private val executor = Executors.newFixedThreadPool(THREADS)

    private var adminId = 0L

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE tb_expo_deletion, tb_trainee, tb_standard_participant, tb_admin RESTART IDENTITY CASCADE",
        )
        adminId = admin("관리자", "ROLE_ADMIN")
    }

    @AfterEach
    fun tearDown() {
        executor.shutdownNow()
    }

    @Test
    fun `확인된 레거시 연수자를 참가자에 이으면 ID와 기존 값·출석은 그대로이고 승인자와 근거를 남긴다`() {
        val participant = participant("대표", PHONE)
        val trainee = legacyTrainee("대표", PHONE, trainingId = "T-1")
        attend(trainee)

        link(trainee, participant)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.traineeId").value(trainee))
            .andExpect(jsonPath("$.participantId").value(participant))
            .andExpect(jsonPath("$.changed").value(true))

        val row = jdbcTemplate.queryForMap("SELECT * FROM tb_trainee WHERE id = ?", trainee)
        row["standard_participant_id"] shouldBe participant
        row["phone_number"] shouldBe PHONE
        row["training_id"] shouldBe "T-1"
        count("tb_trainee") shouldBe 1
        count("tb_trainee_participation") shouldBe 1
        val audit = jdbcTemplate.queryForMap("SELECT * FROM tb_trainee_participant_link_audit")
        audit["action"] shouldBe "LINK"
        audit["actor_admin_id"] shouldBe adminId
        audit["reason"] shouldBe REASON
        audit["standard_participant_id"] shouldBe participant
    }

    @Test
    fun `이은 뒤에는 기존 조회와 참가자 기준 연수자 조회가 같은 레거시 ID를 돌려주고 새로 만들지 않는다`() {
        val participant = participant("대표", PHONE)
        val trainee = legacyTrainee("대표", PHONE)
        link(trainee, participant).andExpect(status().isOk)

        post("/internal/trainees/resolve-or-create-by-participant", """{"expoId":"$EXPO","participantId":$participant}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.traineeId").value(trainee))
            .andExpect(jsonPath("$.created").value(false))
        post("/internal/standard-participants/admin-cancellation-status", """{"expoId":"$EXPO","participantId":$participant}""")
            .andExpect(jsonPath("$.linkedTraineeId").value(trainee))
        details(trainee).andExpect(jsonPath("$.participantId").value(participant))
        count("tb_trainee") shouldBe 1
    }

    @Test
    fun `같은 요청을 다시 보내면 바꾸지 않고 감사 기록도 더 남기지 않는다`() {
        val participant = participant("대표", PHONE)
        val trainee = legacyTrainee("대표", PHONE)

        link(trainee, participant).andExpect(jsonPath("$.changed").value(true))
        link(trainee, participant).andExpect(status().isOk).andExpect(jsonPath("$.changed").value(false))

        count("tb_trainee_participant_link_audit") shouldBe 1
    }

    @Test
    fun `같은 대표 번호의 동행자들은 요청한 연수자에만 이어지고 번호로 다른 사람에 이어지지 않는다`() {
        val representative = participant("대표", PHONE)
        val first = participant("동행1", null, representativeId = representative)
        val second = participant("동행2", null, representativeId = representative)
        val firstTrainee = legacyTrainee("동행1", PHONE)
        val secondTrainee = legacyTrainee("동행2", "010-1234-5678")

        link(firstTrainee, first).andExpect(status().isOk)

        linkOf(secondTrainee) shouldBe null
        linkOf(firstTrainee) shouldBe first
        link(firstTrainee, second).andExpect(status().isConflict)
        link(secondTrainee, second).andExpect(status().isOk)
        linkedTraineeOf(representative) shouldBe null
    }

    @Test
    fun `이미 다른 연수자가 이어진 참가자나 다른 참가자에 이어진 연수자는 덮어쓰지 않고 409이다`() {
        val participant = participant("대표", PHONE)
        val other = participant("다른사람", "01077778888")
        val created =
            post("/internal/trainees/resolve-or-create-by-participant", """{"expoId":"$EXPO","participantId":$participant}""")
                .andReturn()
                .response.contentAsString
                .substringAfter("\"traineeId\":")
                .substringBefore(',')
                .toLong()
        val legacy = legacyTrainee("대표", PHONE)

        link(legacy, participant).andExpect(status().isConflict)
        link(created, other).andExpect(status().isConflict)

        linkOf(legacy) shouldBe null
        linkOf(created) shouldBe participant
        count("tb_trainee_participant_link_audit") shouldBe 0
    }

    @Test
    fun `다른 박람회이거나 없는 연수자·참가자는 구분 없이 404이고 아무것도 바꾸지 않는다`() {
        val participant = participant("대표", PHONE)
        val otherExpoParticipant = participant("다른행사", PHONE, expoId = OTHER_EXPO)
        val trainee = legacyTrainee("대표", PHONE)
        val otherExpoTrainee = legacyTrainee("다른행사", PHONE, expoId = OTHER_EXPO)

        link(trainee, otherExpoParticipant).andExpect(status().isNotFound)
        link(otherExpoTrainee, participant).andExpect(status().isNotFound)
        link(trainee, participant + 1000).andExpect(status().isNotFound)
        link(trainee + 1000, participant).andExpect(status().isNotFound)

        linkOf(trainee) shouldBe null
        linkOf(otherExpoTrainee) shouldBe null
        count("tb_trainee_participant_link_audit") shouldBe 0
    }

    @Test
    fun `승인자가 없거나 관리자 권한이 아니면 403이고 근거가 없으면 400이다`() {
        val participant = participant("대표", PHONE)
        val trainee = legacyTrainee("대표", PHONE)
        val pending = admin("대기", "ROLE_STANDARD")

        link(trainee, participant, actor = pending).andExpect(status().isForbidden)
        link(trainee, participant, actor = 9999).andExpect(status().isForbidden)
        link(trainee, participant, reason = " ").andExpect(status().isBadRequest)
        post(LINK, """{"expoId":"$EXPO","traineeId":$trainee,"participantId":$participant,"reason":"$REASON"}""")
            .andExpect(status().isBadRequest)

        linkOf(trainee) shouldBe null
    }

    @Test
    fun `한 연수자를 여러 참가자에 동시에 이으면 하나만 이어지고 나머지는 409이다`() {
        val trainee = legacyTrainee("대표", PHONE)
        val participants = List(THREADS) { participant("후보$it", "0101111000$it") }

        val statuses = concurrently(participants) { link(trainee, it).andReturn().response.status }

        statuses.count { it == 200 } shouldBe 1
        statuses.count { it == 409 } shouldBe THREADS - 1
        count("tb_trainee_participant_link_audit") shouldBe 1
        (linkOf(trainee) in participants) shouldBe true
    }

    @Test
    fun `여러 레거시 연수자를 한 참가자에 동시에 이으면 하나만 이어진다`() {
        val participant = participant("대표", PHONE)
        val trainees = List(THREADS) { legacyTrainee("후보$it", "0101111000$it") }

        val statuses = concurrently(trainees) { link(it, participant).andReturn().response.status }

        statuses.count { it == 200 } shouldBe 1
        trainees.count { linkOf(it) == participant } shouldBe 1
    }

    @Test
    fun `이 API로 이은 연결은 해제할 수 있고 신청·출석은 옮기거나 지우지 않으며 해제 뒤 다른 참가자에 다시 이을 수 있다`() {
        val wrong = participant("잘못", PHONE)
        val right = participant("맞음", "01077778888")
        val trainee = legacyTrainee("맞음", "01077778888")
        attend(trainee)
        link(trainee, wrong).andExpect(status().isOk)

        revoke(trainee, wrong)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participantId").doesNotExist())
            .andExpect(jsonPath("$.changed").value(true))
        revoke(trainee, wrong).andExpect(status().isOk).andExpect(jsonPath("$.changed").value(false))
        link(trainee, right).andExpect(status().isOk)

        linkOf(trainee) shouldBe right
        count("tb_trainee_participation") shouldBe 1
        jdbcTemplate.queryForList(
            "SELECT action FROM tb_trainee_participant_link_audit ORDER BY id",
            String::class.java,
        ) shouldBe listOf("LINK", "REVOKE", "LINK")
    }

    @Test
    fun `참가자에서 만든 연수자와 다른 참가자의 연결은 해제할 수 없다`() {
        val participant = participant("대표", PHONE)
        val other = participant("다른사람", "01077778888")
        val created =
            post("/internal/trainees/resolve-or-create-by-participant", """{"expoId":"$EXPO","participantId":$participant}""")
                .andReturn()
                .response.contentAsString
                .substringAfter("\"traineeId\":")
                .substringBefore(',')
                .toLong()
        val legacy = legacyTrainee("다른사람", "01077778888")
        link(legacy, other).andExpect(status().isOk)

        revoke(created, participant).andExpect(status().isConflict)
        revoke(legacy, participant).andExpect(status().isConflict)

        linkOf(created) shouldBe participant
        linkOf(legacy) shouldBe other
    }

    @Test
    fun `조회는 연결 상태를 바꾸지 않고 미연결은 participantId가 null이다`() {
        val participant = participant("대표", PHONE)
        val trainee = legacyTrainee("대표", PHONE)

        details(trainee).andExpect(status().isOk).andExpect(jsonPath("$.participantId").doesNotExist())
        post("/internal/standard-participants/admin-cancellation-status", """{"expoId":"$EXPO","participantId":$participant}""")
            .andExpect(jsonPath("$.traineeLinked").value(false))

        linkOf(trainee) shouldBe null
        count("tb_trainee") shouldBe 1
        count("tb_trainee_participant_link_audit") shouldBe 0
    }

    @Test
    fun `삭제 중인 박람회는 409이고 박람회를 삭제하면 감사 기록도 지운다`() {
        val participant = participant("대표", PHONE)
        val trainee = legacyTrainee("대표", PHONE)
        link(trainee, participant).andExpect(status().isOk)

        mockMvc.perform(delete("/internal/expos/$EXPO").header("X-Internal-Token", INTERNAL_TOKEN)).andExpect(status().isNoContent)

        count("tb_trainee_participant_link_audit") shouldBe 0
        link(trainee, participant).andExpect(status().isConflict)
    }

    @Test
    fun `내부 토큰이 없거나 관리자 토큰만 있으면 401이다`() {
        val participant = participant("대표", PHONE)
        val trainee = legacyTrainee("대표", PHONE)
        val body = body(trainee, participant, adminId, REASON)
        listOf(LINK, REVOKE).forEach { path ->
            mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized)
            mockMvc
                .perform(post(path).header("Authorization", bearerOf(adminId)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized)
        }
        linkOf(trainee) shouldBe null
    }

    private fun <T, R> concurrently(
        inputs: List<T>,
        request: (T) -> R,
    ): List<R> {
        val start = CountDownLatch(1)
        val futures =
            inputs.map {
                executor.submit<R> {
                    start.await()
                    request(it)
                }
            }
        start.countDown()
        return futures.map { it.get(30, TimeUnit.SECONDS) }
    }

    private fun link(
        traineeId: Long,
        participantId: Long,
        actor: Long = adminId,
        reason: String = REASON,
    ) = post(LINK, body(traineeId, participantId, actor, reason))

    private fun revoke(
        traineeId: Long,
        participantId: Long,
    ) = post(REVOKE, body(traineeId, participantId, adminId, REASON))

    private fun body(
        traineeId: Long,
        participantId: Long,
        actor: Long,
        reason: String,
    ) = """{"expoId":"$EXPO","traineeId":$traineeId,"participantId":$participantId,"actorAdminId":$actor,"reason":"$reason"}"""

    private fun details(traineeId: Long): ResultActions =
        mockMvc.perform(get("/internal/trainees/$traineeId/details").header("X-Internal-Token", INTERNAL_TOKEN))

    private fun post(
        path: String,
        body: String,
    ): ResultActions =
        mockMvc.perform(post(path).header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun linkOf(traineeId: Long): Long? =
        jdbcTemplate.queryForObject("SELECT standard_participant_id FROM tb_trainee WHERE id = ?", Long::class.javaObjectType, traineeId)

    private fun linkedTraineeOf(participantId: Long): Long? =
        jdbcTemplate
            .queryForList("SELECT id FROM tb_trainee WHERE standard_participant_id = ?", Long::class.java, participantId)
            .singleOrNull()

    private fun count(table: String) = jdbcTemplate.queryForObject("SELECT count(*) FROM $table", Long::class.java)

    private fun attend(traineeId: Long) {
        jdbcTemplate.update(
            "INSERT INTO tb_trainee_participation (entry_time, attendance_date, trainee_id, expo_id) VALUES (now(), ?, ?, ?)",
            LocalDate.of(2026, 10, 1),
            traineeId,
            EXPO,
        )
    }

    private fun admin(
        name: String,
        authority: String,
    ): Long =
        jdbcTemplate.queryForObject(
            "INSERT INTO tb_admin (name, nickname, email, password, phone_number, authority, status) " +
                "VALUES (?, ?, ?, 'x', ?, ?, 'ACCEPTED') RETURNING id",
            Long::class.java,
            name,
            "nick-$name",
            "$name@example.com",
            "0109${name.hashCode().toString().takeLast(7).padStart(7, '0')}",
            authority,
        )!!

    private fun legacyTrainee(
        name: String,
        phone: String,
        trainingId: String? = null,
        expoId: String = EXPO,
    ): Long =
        traineeRepository
            .save(
                Trainee(
                    expoId = expoId,
                    name = name,
                    phoneNumber = phone,
                    trainingId = trainingId,
                    personalInformationStatus = true,
                    applicationType = ApplicationType.PRE,
                    applicationDate = LocalDateTime.of(2026, 9, 1, 10, 0),
                ),
            ).id!!

    private fun participant(
        name: String,
        phone: String?,
        representativeId: Long? = null,
        expoId: String = EXPO,
    ): Long =
        standardParticipantRepository
            .save(
                StandardParticipant(
                    expoId = expoId,
                    name = name,
                    phoneNumber = phone,
                    personalInformationStatus = true,
                    applicationType = ApplicationType.PRE,
                    applicationDate = LocalDateTime.of(2026, 10, 1, 10, 0),
                    occupation = Occupation.TEACHER,
                    representativeId = representativeId,
                ),
            ).id!!

    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-000000000b01"
        const val OTHER_EXPO = "0199aaaa-0000-7000-8000-000000000b02"
        const val PHONE = "01012345678"
        const val REASON = "연수원 명단 원본과 신청서 서명 대조"
        const val LINK = "/internal/trainees/participant-link"
        const val REVOKE = "/internal/trainees/participant-link/revoke"
        const val THREADS = 6
    }
}
