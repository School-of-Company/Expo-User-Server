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
import team.startup.expo.domain.participation.presentation.dto.response.ParticipantQrResDto
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.support.IntegrationTestSupport
import java.time.LocalDateTime

class ParticipantQrApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var standardParticipantRepository: StandardParticipantRepository

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE tb_expo_deletion, tb_standard_participant_participation, tb_standard_participant RESTART IDENTITY CASCADE",
        )
    }

    @Test
    fun `대표자 본인과 그 동행자의 QR 값을 돌려준다`() {
        val representative = participant("대표", PHONE)
        val companion = participant("동행", null, representative)

        qr(EXPO, representative, representative)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participantId").value(representative))
            .andExpect(jsonPath("$.code").value(codeOf(representative)))
        qr(EXPO, representative, companion)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participantId").value(companion))
            .andExpect(jsonPath("$.code").value(codeOf(companion)))
    }

    @Test
    fun `응답에는 participantId와 code만 있고 전화번호나 이름은 없다`() {
        val representative = participant("대표", PHONE)

        val body =
            qr(EXPO, representative, representative)
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        val fields = Regex("\"(\\w+)\":").findAll(body).map { it.groupValues[1] }.toSet()
        fields shouldBe setOf("participantId", "code")
    }

    @Test
    fun `다른 대표자의 일행과 다른 박람회와 없는 참가자와 대표자가 아닌 사람은 구분 없이 같은 404이다`() {
        val mine = participant("대표", PHONE)
        val other = participant("남대표", OTHER_PHONE)
        val otherCompanion = participant("남동행", null, other)
        val otherExpo = participant("다른행사", "01099990000", expoId = OTHER_EXPO)
        val companion = participant("동행", null, mine)

        val responses =
            listOf(
                qr(EXPO, mine, other), // 다른 대표자 본인
                qr(EXPO, mine, otherCompanion), // 다른 대표자의 일행
                qr(EXPO, mine, otherExpo), // 다른 박람회의 참가자
                qr(EXPO, mine, otherExpo + 1000), // 없는 참가자
                qr(EXPO, companion, mine), // 대표자가 아닌 사람을 대표자로 넘김
                qr(EXPO, companion, companion), // 동행자를 대표자로 넘기고 본인을 조회
                qr(EXPO, mine + 1000, mine), // 없는 대표자
            ).map {
                it
                    .andExpect(status().isNotFound)
                    .andReturn()
                    .response.contentAsString
            }

        responses.toSet().size shouldBe 1
    }

    @Test
    fun `404 응답에는 code가 들어 있지 않다`() {
        val mine = participant("대표", PHONE)
        val other = participant("남대표", OTHER_PHONE)

        val body =
            qr(EXPO, mine, other)
                .andExpect(status().isNotFound)
                .andReturn()
                .response.contentAsString

        check(!body.contains(codeOf(other)) && !body.contains(codeOf(mine))) { "오류 응답에 code가 있으면 안 된다" }
    }

    @Test
    fun `아무것도 기록하거나 바꾸지 않는다`() {
        val representative = participant("대표", PHONE)
        val before = jdbcTemplate.queryForList("SELECT id, sms_try_time, code FROM tb_standard_participant ORDER BY id")

        qr(EXPO, representative, representative).andExpect(status().isOk)

        jdbcTemplate.queryForList("SELECT id, sms_try_time, code FROM tb_standard_participant ORDER BY id") shouldBe before
        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_standard_participant_participation", Long::class.java) shouldBe 0L
    }

    @Test
    fun `응답 객체의 문자열 표현은 code를 가린다`() {
        ParticipantQrResDto(1L, "secret-code-value").toString().contains("secret-code-value") shouldBe false
    }

    @Test
    fun `필수 값이 없거나 올바르지 않으면 400이다`() {
        request("""{"expoId":"$EXPO","participantId":1}""").andExpect(status().isBadRequest)
        request("""{"expoId":"$EXPO","representativeId":1}""").andExpect(status().isBadRequest)
        request("""{"representativeId":1,"participantId":1}""").andExpect(status().isBadRequest)
        request("""{"expoId":"$EXPO","representativeId":0,"participantId":1}""").andExpect(status().isBadRequest)
    }

    @Test
    fun `토큰이 없거나 틀리거나 관리자 토큰만 있으면 401이다`() {
        val representative = participant("대표", PHONE)
        val body = """{"expoId":"$EXPO","representativeId":$representative,"participantId":$representative}"""
        val path = "/internal/standard-participants/qr"

        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized)
        mockMvc
            .perform(post(path).header("X-Internal-Token", "wrong").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)
        mockMvc
            .perform(post(path).header("Authorization", bearerOf(1L)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)
    }

    private fun qr(
        expoId: String,
        representativeId: Long,
        participantId: Long,
    ): ResultActions = request("""{"expoId":"$expoId","representativeId":$representativeId,"participantId":$participantId}""")

    private fun bodyOf(
        result: ResultActions,
        expectedStatus: Int,
    ): String {
        val response = result.andReturn().response
        response.status shouldBe expectedStatus
        return response.contentAsString
    }

    private fun request(body: String): ResultActions =
        mockMvc.perform(
            post("/internal/standard-participants/qr")
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )

    private fun codeOf(participantId: Long): String =
        jdbcTemplate.queryForObject("SELECT code FROM tb_standard_participant WHERE id = ?", String::class.java, participantId)!!

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
        const val EXPO = "0199aaaa-0000-7000-8000-000000000c01"
        const val OTHER_EXPO = "0199aaaa-0000-7000-8000-000000000c02"
        const val PHONE = "01012345678"
        const val OTHER_PHONE = "01055556666"
    }
}
