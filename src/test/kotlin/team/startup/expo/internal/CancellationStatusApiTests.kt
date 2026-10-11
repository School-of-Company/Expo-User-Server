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

class CancellationStatusApiTests : IntegrationTestSupport() {
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
    fun `입장하지 않고 연결도 없는 참가자는 미입장 미연결로 답한다`() {
        val representative = participant("대표", PHONE)

        lookup(PHONE, representative)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participantId").value(representative))
            .andExpect(jsonPath("$.representative").value(true))
            .andExpect(jsonPath("$.entered").value(false))
            .andExpect(jsonPath("$.traineeLinked").value(false))
            .andExpect(jsonPath("$.linkedTraineeId").doesNotExist())
    }

    @Test
    fun `어느 날짜든 입장 기록이 있으면 입장으로 답한다`() {
        val representative = participant("대표", PHONE)
        val companion = participant("동행", null, representative)
        enter(companion, "2026-09-01")

        lookup(PHONE, companion)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.representative").value(false))
            .andExpect(jsonPath("$.entered").value(true))
        lookup(PHONE, representative).andExpect(jsonPath("$.entered").value(false))
    }

    @Test
    fun `이어진 연수자가 있으면 그 id를 알려 준다`() {
        val representative = participant("대표", PHONE)
        val traineeId = trainee(representative)

        lookup(PHONE, representative)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.traineeLinked").value(true))
            .andExpect(jsonPath("$.linkedTraineeId").value(traineeId))
    }

    @Test
    fun `다른 사람과 다른 박람회와 없는 참가자는 구분 없이 같은 404이다`() {
        val mine = participant("대표", PHONE)
        val other = participant("남대표", OTHER_PHONE)
        val otherCompanion = participant("남동행", null, other)
        val otherExpo = participant("다른행사", "01099990000", expoId = OTHER_EXPO)

        val bodies =
            listOf(
                lookup(PHONE, other),
                lookup(PHONE, otherCompanion),
                lookup(PHONE, otherExpo),
                lookup(PHONE, otherExpo + 1000),
                lookup("01000000000", mine), // 등록되지 않은 번호
            ).map {
                it
                    .andExpect(status().isNotFound)
                    .andReturn()
                    .response.contentAsString
            }

        bodies.toSet().size shouldBe 1
    }

    @Test
    fun `아무것도 만들거나 바꾸지 않는다`() {
        val representative = participant("대표", PHONE)
        val before = jdbcTemplate.queryForList("SELECT id, sms_try_time, code FROM tb_standard_participant ORDER BY id")

        lookup(PHONE, representative).andExpect(status().isOk)

        jdbcTemplate.queryForList("SELECT id, sms_try_time, code FROM tb_standard_participant ORDER BY id") shouldBe before
        count("tb_trainee") shouldBe 0L
        count("tb_standard_participant_participation") shouldBe 0L
    }

    @Test
    fun `필수 값이 없으면 400이다`() {
        request("""{"expoId":"$EXPO","phoneNumber":"$PHONE"}""").andExpect(status().isBadRequest)
        request("""{"expoId":"$EXPO","participantId":1}""").andExpect(status().isBadRequest)
        request("""{"phoneNumber":"$PHONE","participantId":1}""").andExpect(status().isBadRequest)
    }

    @Test
    fun `토큰이 없거나 틀리거나 관리자 토큰만 있으면 401이다`() {
        val body = """{"expoId":"$EXPO","phoneNumber":"$PHONE","participantId":1}"""
        val path = "/internal/standard-participants/cancellation-status"

        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized)
        mockMvc
            .perform(post(path).header("X-Internal-Token", "wrong").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)
        mockMvc
            .perform(post(path).header("Authorization", bearerOf(1L)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)
    }

    private fun lookup(
        phone: String,
        participantId: Long,
        expoId: String = EXPO,
    ): ResultActions = request("""{"expoId":"$expoId","phoneNumber":"$phone","participantId":$participantId}""")

    private fun request(body: String): ResultActions =
        mockMvc.perform(
            post("/internal/standard-participants/cancellation-status")
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
        const val EXPO = "0199aaaa-0000-7000-8000-000000000d01"
        const val OTHER_EXPO = "0199aaaa-0000-7000-8000-000000000d02"
        const val PHONE = "01012345678"
        const val OTHER_PHONE = "01055556666"
    }
}
