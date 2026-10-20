package team.startup.expo.internal

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.support.IntegrationTestSupport

class RegistrationStatusApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE tb_expo_deletion, tb_registration_request, tb_registration_cancellation, tb_registration_outbox, " +
                "tb_trainee, tb_standard_participant RESTART IDENTITY CASCADE",
        )
    }

    @Test
    fun `저장된 요청은 SAVED와 대표자 ID만 답한다`() {
        register(REQUEST_A).andExpect(status().isCreated)

        status(REQUEST_A)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("SAVED"))
            .andExpect(jsonPath("$.participantId").isNumber)
            .andExpect(jsonPath("$.phoneNumber").doesNotExist())
    }

    @Test
    fun `저장되지 않은 요청은 NOT_SAVED를 답하고 이후 같은 키의 등록은 409이며 저장하지 않는다`() {
        status(REQUEST_B)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("NOT_SAVED"))

        register(REQUEST_B).andExpect(status().isConflict)

        status(REQUEST_B).andExpect(jsonPath("$.status").value("NOT_SAVED"))
        val saved = jdbcTemplate.queryForObject("SELECT count(*) FROM tb_standard_participant", Long::class.java)
        check(saved == 0L)
    }

    @Test
    fun `다른 박람회에 쓰인 취소 기록의 키는 409이다`() {
        status(REQUEST_B).andExpect(status().isOk)

        post("/internal/standard-participants/registration-status", """{"expoId":"$OTHER_EXPO","requestId":"$REQUEST_B"}""")
            .andExpect(status().isConflict)
    }

    @Test
    fun `삭제 중인 박람회는 409이다`() {
        jdbcTemplate.update("INSERT INTO tb_expo_deletion (expo_id, started_at) VALUES (?, now())", EXPO)

        status(REQUEST_A).andExpect(status().isConflict)
    }

    private fun status(requestId: String): ResultActions =
        post("/internal/standard-participants/registration-status", """{"expoId":"$EXPO","requestId":"$requestId"}""")

    private fun register(requestId: String): ResultActions =
        post(
            "/internal/standard-participants",
            """{"expoId":"$EXPO","name":"홍길동","phoneNumber":"01012345678","informationJson":"{}","personalInformationStatus":true,""" +
                """"applicationType":"PRE","requestId":"$requestId"}""",
        )

    private fun post(
        path: String,
        body: String,
    ): ResultActions =
        mockMvc.perform(post(path).header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON).content(body))

    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-0000000000b1"
        const val OTHER_EXPO = "0199aaaa-0000-7000-8000-0000000000b2"
        const val REQUEST_A = "request-a"
        const val REQUEST_B = "request-b"
    }
}
