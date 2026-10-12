package team.startup.expo.internal

import io.kotest.matchers.shouldBe
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
import team.startup.expo.support.IntegrationTestSupport
import java.time.LocalDateTime

class AdminCancellationStatusApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var standardParticipantRepository: StandardParticipantRepository

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE tb_expo_deletion, tb_standard_participant_participation, tb_trainee, tb_standard_participant " +
                "RESTART IDENTITY CASCADE",
        )
    }

    @Test
    fun `전화번호 없이 입장하지 않고 연결도 없는 참가자는 미입장 미연결로 답한다`() {
        val id = participant("참가자", PHONE)

        lookup(EXPO, id)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participantId").value(id))
            .andExpect(jsonPath("$.entered").value(false))
            .andExpect(jsonPath("$.traineeLinked").value(false))
            .andExpect(jsonPath("$.linkedTraineeId").doesNotExist())
    }

    @Test
    fun `대표자가 아닌 동행자도 소유 확인 없이 조회하고 어느 날짜든 입장 기록이 있으면 입장이다`() {
        val representative = participant("대표", PHONE)
        val companion = participant("동행", null, representative)
        enter(companion, "2026-09-01")

        lookup(EXPO, companion)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.entered").value(true))
        lookup(EXPO, representative).andExpect(jsonPath("$.entered").value(false))
    }

    @Test
    fun `이어진 연수자가 있으면 그 id를 알려 준다`() {
        val id = participant("참가자", PHONE)
        val traineeId = trainee(id)

        lookup(EXPO, id)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.traineeLinked").value(true))
            .andExpect(jsonPath("$.linkedTraineeId").value(traineeId))
    }

    @Test
    fun `없는 참가자와 다른 박람회의 참가자는 구분 없이 같은 404이다`() {
        val mine = participant("참가자", PHONE)
        val otherExpo = participant("다른행사", "01099990000", expoId = OTHER_EXPO)

        val bodies =
            listOf(
                lookup(EXPO, otherExpo),
                lookup(EXPO, mine + 1000),
                lookup(OTHER_EXPO, mine),
            ).map {
                it
                    .andExpect(status().isNotFound)
                    .andReturn()
                    .response.contentAsString
            }

        bodies.toSet().size shouldBe 1
    }

    @Test
    fun `응답에는 참가자 id와 상태만 있고 전화번호나 code는 없다`() {
        val id = participant("참가자", PHONE)

        val body =
            lookup(EXPO, id)
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        Regex("\"(\\w+)\":").findAll(body).map { it.groupValues[1] }.toSet() shouldBe
            setOf("participantId", "entered", "traineeLinked", "linkedTraineeId")
    }

    @Test
    fun `아무것도 만들거나 바꾸지 않는다`() {
        val id = participant("참가자", PHONE)
        val before = jdbcTemplate.queryForList("SELECT id, sms_try_time, code FROM tb_standard_participant ORDER BY id")

        lookup(EXPO, id).andExpect(status().isOk)

        jdbcTemplate.queryForList("SELECT id, sms_try_time, code FROM tb_standard_participant ORDER BY id") shouldBe before
        count("tb_trainee") shouldBe 0L
        count("tb_standard_participant_participation") shouldBe 0L
    }

    @Test
    fun `필수 값이 없으면 400이다`() {
        request("""{"expoId":"$EXPO"}""").andExpect(status().isBadRequest)
        request("""{"participantId":1}""").andExpect(status().isBadRequest)
    }

    @Test
    fun `토큰이 없거나 틀리거나 관리자 토큰만 있으면 401이다`() {
        val body = """{"expoId":"$EXPO","participantId":1}"""
        val path = "/internal/standard-participants/admin-cancellation-status"

        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized)
        mockMvc
            .perform(post(path).header("X-Internal-Token", "wrong").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)
        mockMvc
            .perform(post(path).header("Authorization", bearerOf(1L)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)
    }

    private fun lookup(
        expoId: String,
        participantId: Long,
    ): ResultActions = request("""{"expoId":"$expoId","participantId":$participantId}""")

    private fun request(body: String): ResultActions =
        mockMvc.perform(
            post("/internal/standard-participants/admin-cancellation-status")
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )

    private fun enter(
        participantId: Long,
        date: String,
    ) {
        jdbcTemplate.update(
            "INSERT INTO tb_standard_participant_participation (entry_time, attendance_date, standard_participant_id, expo_id) " +
                "VALUES (now(), ?::date, ?, ?)",
            date,
            participantId,
            EXPO,
        )
    }

    private fun trainee(participantId: Long): Long =
        jdbcTemplate.queryForObject(
            "INSERT INTO tb_trainee (expo_id, name, phone_number, training_id, personal_information_status, application_type, " +
                "application_date, standard_participant_id) VALUES (?, '연수', '01012345678', 'T-1', true, 'PRE', now(), ?) RETURNING id",
            Long::class.java,
            EXPO,
            participantId,
        )!!

    private fun count(table: String) = jdbcTemplate.queryForObject("SELECT count(*) FROM $table", Long::class.java)

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
        const val EXPO = "0199aaaa-0000-7000-8000-000000000d11"
        const val OTHER_EXPO = "0199aaaa-0000-7000-8000-000000000d12"
        const val PHONE = "01012345678"
    }
}
