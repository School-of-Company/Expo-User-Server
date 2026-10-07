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
import team.startup.expo.support.IntegrationTestSupport

/** 등록 API가 참가자 저장과 같은 트랜잭션에서 아웃박스 이벤트를 남기는지 확인한다. 발행은 [RegistrationOutboxRelayTests]에서 본다. */
class RegistrationOutboxApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE tb_expo_deletion, tb_registration_request, tb_registration_outbox, tb_trainee, tb_standard_participant " +
                "RESTART IDENTITY CASCADE",
        )
    }

    @Test
    fun `새 일반 참가자는 등록 완료와 신규 저장 이벤트를 남기고 아직 발행하지 않은 상태이다`() {
        standard(PHONE).andExpect(status().isCreated)

        val events = events()
        events.map { it["event_type"] } shouldBe listOf("REGISTERED", "STANDARD_CREATED")
        events.all { it["participation_type"] == "STANDARD" && it["expo_id"] == EXPO && it["phone_number"] == PHONE } shouldBe true
        events.all { it["participant_id"] == participantId() } shouldBe true
        events.all { it["published_at"] == null && it["attempts"] == 0 } shouldBe true
        events.map { (it["event_id"] as String).length }.all { it == 36 } shouldBe true
        events.map { it["event_id"] }.toSet().size shouldBe 2
    }

    @Test
    fun `QR 재발송이 허용된 기존 참가자는 등록 완료 이벤트만 새로 남긴다`() {
        standard(PHONE).andExpect(status().isCreated)

        standard(PHONE).andExpect(status().isOk)

        events().map { it["event_type"] } shouldBe listOf("REGISTERED", "STANDARD_CREATED", "REGISTERED")
        events().map { it["event_id"] }.toSet().size shouldBe 3
    }

    @Test
    fun `연수 사전 등록은 이벤트를 남기지 않는다`() {
        trainee("T-1", "PRE").andExpect(status().isCreated)

        count("tb_registration_outbox") shouldBe 0
    }

    @Test
    fun `연수 현장 등록은 등록 완료 이벤트를 남긴다`() {
        trainee("T-1", "FIELD").andExpect(status().isCreated)

        val events = events()
        events.map { it["event_type"] } shouldBe listOf("REGISTERED")
        events.single()["participation_type"] shouldBe "TRAINEE"
        events.single()["participant_id"] shouldBe jdbcTemplate.queryForObject("SELECT id FROM tb_trainee", Long::class.java)
    }

    @Test
    fun `거부된 등록은 참가자도 이벤트도 남기지 않는다`() {
        standard(PHONE).andExpect(status().isCreated)
        repeat(2) { smsTry() }
        val before = count("tb_registration_outbox")

        standard(PHONE).andExpect(status().isConflict)

        count("tb_registration_outbox") shouldBe before
    }

    @Test
    fun `삭제된 박람회의 등록은 이벤트를 남기지 않는다`() {
        jdbcTemplate.update("INSERT INTO tb_expo_deletion (expo_id, started_at) VALUES (?, now())", EXPO)

        standard(PHONE).andExpect(status().isConflict)

        count("tb_registration_outbox") shouldBe 0
        count("tb_standard_participant") shouldBe 0
    }

    private fun events() = jdbcTemplate.queryForList("SELECT * FROM tb_registration_outbox ORDER BY id")

    private fun participantId() = jdbcTemplate.queryForObject("SELECT id FROM tb_standard_participant", Long::class.java)

    private fun standard(phone: String): ResultActions =
        post(
            "/internal/standard-participants",
            """{"expoId":"$EXPO","name":"홍길동","phoneNumber":"$phone","informationJson":"{}","personalInformationStatus":true,""" +
                """"applicationType":"PRE"}""",
        )

    private fun trainee(
        training: String,
        applicationType: String,
    ): ResultActions =
        post(
            "/internal/trainees",
            """{"expoId":"$EXPO","trainingId":"$training","name":"연수자","phoneNumber":"$PHONE","informationJson":"{}",""" +
                """"personalInformationStatus":true,"applicationType":"$applicationType"}""",
        )

    private fun smsTry(): ResultActions =
        post(
            "/internal/participants/sms-try",
            """{"expoId":"$EXPO","participationType":"STANDARD","phoneNumber":"$PHONE","eventId":"${java.util.UUID.randomUUID()}"}""",
        )

    private fun post(
        path: String,
        body: String,
    ): ResultActions =
        mockMvc.perform(post(path).header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun count(table: String) = jdbcTemplate.queryForObject("SELECT count(*) FROM $table", Long::class.java)

    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-0000000000c1"
        const val PHONE = "01012345678"
    }
}
