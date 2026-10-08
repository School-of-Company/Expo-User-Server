package team.startup.expo.internal

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.participation.entity.Occupation
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.support.IntegrationTestSupport
import java.time.LocalDateTime

class VerifyStandardParticipantApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var standardParticipantRepository: StandardParticipantRepository

    private lateinit var participant: StandardParticipant

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE tb_standard_participant_participation, tb_standard_participant RESTART IDENTITY CASCADE",
        )
        participant =
            standardParticipantRepository.save(
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
            )
    }

    @Test
    fun `참가자 id와 code가 맞으면 204이고 입장은 기록하지 않는다`() {
        verify(EXPO, participant.id!!, participant.code).andExpect(status().isNoContent)
        verify(EXPO, participant.id!!, participant.code).andExpect(status().isNoContent)

        jdbcTemplate.queryForObject("SELECT count(*) FROM tb_standard_participant_participation", Long::class.java) shouldBe 0
    }

    @Test
    fun `code가 다르거나 참가자가 없거나 다른 박람회면 모두 404다`() {
        verify(EXPO, participant.id!!, "x".repeat(22)).andExpect(status().isNotFound)
        verify(EXPO, participant.id!! + 100, participant.code).andExpect(status().isNotFound)
        verify(OTHER_EXPO, participant.id!!, participant.code).andExpect(status().isNotFound)
    }

    @Test
    fun `필수 값이 없으면 400이다`() {
        request("""{"expoId":"$EXPO","code":"${participant.code}"}""").andExpect(status().isBadRequest)
        request("""{"expoId":"$EXPO","participantId":${participant.id}}""").andExpect(status().isBadRequest)
    }

    @Test
    fun `내부 토큰이 없으면 401이다`() {
        mockMvc
            .perform(
                post("/internal/standard-participants/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"expoId":"$EXPO","participantId":${participant.id},"code":"${participant.code}"}"""),
            ).andExpect(status().isUnauthorized)
    }

    private fun verify(
        expoId: String,
        participantId: Long,
        code: String,
    ): ResultActions = request("""{"expoId":"$expoId","participantId":$participantId,"code":"$code"}""")

    private fun request(body: String): ResultActions =
        mockMvc.perform(
            post("/internal/standard-participants/verify")
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )

    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-0000000000c1"
        const val OTHER_EXPO = "0199aaaa-0000-7000-8000-0000000000c2"
    }
}
