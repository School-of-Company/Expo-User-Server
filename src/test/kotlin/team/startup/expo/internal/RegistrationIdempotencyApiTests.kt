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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.support.IntegrationTestSupport
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RegistrationIdempotencyApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    private val executor = Executors.newFixedThreadPool(THREADS)

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE tb_expo_deletion, tb_registration_request, tb_registration_outbox, tb_trainee, tb_standard_participant " +
                "RESTART IDENTITY CASCADE",
        )
    }

    @AfterEach
    fun tearDown() {
        executor.shutdownNow()
    }

    @Test
    fun `같은 requestId로 일반 참가자 등록을 재시도하면 같은 결과를 돌려주고 새로 저장하지도 이벤트를 만들지도 않는다`() {
        val first =
            standard(REQUEST_A)
                .andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString

        val retry =
            standard(REQUEST_A)
                .andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString

        retry shouldBe first
        count("tb_standard_participant") shouldBe 1
        count("tb_registration_outbox") shouldBe 2
        count("tb_registration_request") shouldBe 1
    }

    @Test
    fun `QR 재발송으로 200이었던 등록도 같은 requestId로 재시도하면 200이고 이벤트를 늘리지 않는다`() {
        standard(null).andExpect(status().isCreated)
        standard(REQUEST_A).andExpect(status().isOk)
        val events = count("tb_registration_outbox")

        standard(REQUEST_A).andExpect(status().isOk)

        count("tb_registration_outbox") shouldBe events
    }

    @Test
    fun `같은 requestId로 다른 내용을 보내면 409이다`() {
        standard(REQUEST_A, name = "홍길동").andExpect(status().isCreated)

        standard(REQUEST_A, name = "다른사람").andExpect(status().isConflict)

        count("tb_standard_participant") shouldBe 1
    }

    @Test
    fun `같은 requestId를 일반 참가자와 연수자 등록에 걸쳐 쓰면 409이다`() {
        standard(REQUEST_A).andExpect(status().isCreated)

        trainee(REQUEST_A, "T-1", PHONE).andExpect(status().isConflict)

        count("tb_trainee") shouldBe 0
    }

    @Test
    fun `연수자 등록도 같은 requestId로 재시도하면 같은 결과를 돌려준다`() {
        val first =
            trainee(REQUEST_A, "T-1", PHONE, "FIELD")
                .andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString

        val retry =
            trainee(REQUEST_A, "T-1", PHONE, "FIELD")
                .andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString

        retry shouldBe first
        count("tb_trainee") shouldBe 1
        count("tb_registration_outbox") shouldBe 1
    }

    @Test
    fun `같은 requestId의 동시 재시도는 한 번만 등록하고 모두 같은 결과이다`() {
        val bodies = concurrently { standard(REQUEST_A).andReturn().response }

        bodies.all { it.status == 201 } shouldBe true
        bodies.map { it.contentAsString }.toSet().size shouldBe 1
        count("tb_standard_participant") shouldBe 1
        count("tb_registration_outbox") shouldBe 2
    }

    @Test
    fun `requestId가 없으면 멱등 처리 없이 등록하고 기록도 남기지 않는다`() {
        standard(null).andExpect(status().isCreated)

        count("tb_registration_request") shouldBe 0
    }

    @Test
    fun `비어 있거나 100자를 넘는 requestId는 400이다`() {
        standard("").andExpect(status().isBadRequest)
        standard("k".repeat(101)).andExpect(status().isBadRequest)

        count("tb_standard_participant") shouldBe 0
    }

    @Test
    fun `실패한 등록의 requestId는 기록이 남지 않아 다시 처리한다`() {
        standard(null).andExpect(status().isCreated)
        repeat(2) { smsTry(PHONE) }
        standard(REQUEST_A).andExpect(status().isConflict)
        count("tb_registration_request") shouldBe 0

        jdbcTemplate.update("UPDATE tb_standard_participant SET sms_try_time = 0")

        standard(REQUEST_A).andExpect(status().isOk)
        count("tb_registration_request") shouldBe 1
    }

    @Test
    fun `박람회를 삭제하면 요청 기록과 아웃박스와 문자 이벤트 기록도 지운다`() {
        standard(REQUEST_A).andExpect(status().isCreated)
        smsTry(PHONE)
        count("tb_registration_request") shouldBe 1
        count("tb_registration_outbox") shouldBe 2
        count("tb_sms_try_event") shouldBe 1

        mockMvc
            .perform(delete("/internal/expos/$EXPO").header("X-Internal-Token", INTERNAL_TOKEN))
            .andExpect(status().isNoContent)

        count("tb_registration_request") shouldBe 0
        count("tb_registration_outbox") shouldBe 0
        count("tb_sms_try_event") shouldBe 0
    }

    @Test
    fun `삭제된 박람회에는 같은 requestId로도 다시 등록할 수 없다`() {
        standard(REQUEST_A).andExpect(status().isCreated)
        mockMvc.perform(delete("/internal/expos/$EXPO").header("X-Internal-Token", INTERNAL_TOKEN)).andExpect(status().isNoContent)

        standard(REQUEST_A).andExpect(status().isConflict)

        count("tb_standard_participant") shouldBe 0
    }

    @Test
    fun `삭제가 시작된 박람회는 요청 기록이 남아 있어도 같은 requestId의 재시도에 409이고 기존 ID를 돌려주지 않는다`() {
        standard(REQUEST_A).andExpect(status().isCreated)
        // 삭제 트랜잭션이 기록을 남기고 아직 행을 지우지 못한 상태. 요청 기록은 그대로 있다
        jdbcTemplate.update("INSERT INTO tb_expo_deletion (expo_id, started_at) VALUES (?, now())", EXPO)
        count("tb_registration_request") shouldBe 1

        standard(REQUEST_A).andExpect(status().isConflict)
        trainee(REQUEST_B, "T-1", PHONE).andExpect(status().isConflict)
    }

    @Test
    fun `응답 본문에는 created를 싣지 않는다`() {
        standard(REQUEST_A)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.participantId").isNumber)
            .andExpect(jsonPath("$.created").doesNotExist())
    }

    private fun <T> concurrently(request: () -> T): List<T> {
        val start = CountDownLatch(1)
        val futures =
            List(THREADS) {
                executor.submit<T> {
                    start.await()
                    request()
                }
            }
        start.countDown()
        return futures.map { it.get(30, TimeUnit.SECONDS) }
    }

    private fun standard(
        requestId: String?,
        name: String = "홍길동",
    ): ResultActions =
        post(
            "/internal/standard-participants",
            """{"expoId":"$EXPO","name":"$name","phoneNumber":"$PHONE","informationJson":"{}","personalInformationStatus":true,""" +
                """"applicationType":"PRE","requestId":${json(requestId)}}""",
        )

    private fun trainee(
        requestId: String?,
        training: String,
        phone: String,
        applicationType: String = "PRE",
    ): ResultActions =
        post(
            "/internal/trainees",
            """{"expoId":"$EXPO","trainingId":"$training","name":"연수자","phoneNumber":"$phone","informationJson":"{}",""" +
                """"personalInformationStatus":true,"applicationType":"$applicationType","requestId":${json(requestId)}}""",
        )

    private fun smsTry(phone: String): ResultActions =
        post(
            "/internal/participants/sms-try",
            """{"expoId":"$EXPO","participationType":"STANDARD","phoneNumber":"$phone","eventId":"${java.util.UUID.randomUUID()}"}""",
        )

    private fun post(
        path: String,
        body: String,
    ): ResultActions =
        mockMvc.perform(post(path).header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun json(value: String?) = if (value == null) "null" else "\"$value\""

    private fun count(table: String) = jdbcTemplate.queryForObject("SELECT count(*) FROM $table", Long::class.java)

    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-0000000000b1"
        const val PHONE = "01012345678"
        const val REQUEST_A = "request-a"
        const val REQUEST_B = "request-b"
        const val THREADS = 8
    }
}
