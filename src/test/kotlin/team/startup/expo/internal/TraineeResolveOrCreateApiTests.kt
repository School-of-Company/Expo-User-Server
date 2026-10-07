package team.startup.expo.internal

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.support.TransactionTemplate
import team.startup.expo.domain.participation.service.ExpoDeletionLock
import team.startup.expo.support.IntegrationTestSupport
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TraineeResolveOrCreateApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    private val executor = Executors.newFixedThreadPool(THREADS)

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_expo_deletion, tb_trainee RESTART IDENTITY CASCADE")
    }

    @AfterEach
    fun tearDown() {
        executor.shutdownNow()
    }

    @Test
    fun `없는 연수자는 사전 등록으로 만들고 created가 true이다`() {
        resolveOrCreate(trainingId = "T-1", phone = PHONE)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.traineeId").isNumber)
            .andExpect(jsonPath("$.created").value(true))
            .andExpect(jsonPath("$.phoneNumber").doesNotExist())

        val row = jdbcTemplate.queryForMap("SELECT * FROM tb_trainee WHERE expo_id = ?", EXPO)
        row["training_id"] shouldBe "T-1"
        row["name"] shouldBe "연수자"
        row["application_type"] shouldBe "PRE"
        row["personal_information_status"] shouldBe true
    }

    @Test
    fun `있는 연수자는 그대로 돌려주고 요청 값으로 덮어쓰지 않는다`() {
        val id = resolveOrCreate(trainingId = "T-1", phone = PHONE).andReturn().traineeId()

        resolveOrCreate(trainingId = "T-9", phone = PHONE, name = "다른이름", personalInformationStatus = false)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.traineeId").value(id))
            .andExpect(jsonPath("$.created").value(false))

        count() shouldBe 1
        val row = jdbcTemplate.queryForMap("SELECT * FROM tb_trainee")
        row["name"] shouldBe "연수자"
        row["training_id"] shouldBe "T-1"
        row["personal_information_status"] shouldBe true
    }

    @Test
    fun `같은 요청을 다시 보내면 같은 traineeId를 돌려준다`() {
        val first = resolveOrCreate(trainingId = "T-1", phone = PHONE).andReturn().traineeId()

        repeat(3) {
            resolveOrCreate(trainingId = "T-1", phone = PHONE)
                .andExpect(jsonPath("$.traineeId").value(first))
                .andExpect(jsonPath("$.created").value(false))
        }
        count() shouldBe 1
    }

    @Test
    fun `표기가 달라도 같은 번호면 기존 연수자를 쓴다`() {
        val id = resolveOrCreate(trainingId = "T-1", phone = "010-1234-5678").andReturn().traineeId()

        resolveOrCreate(trainingId = "T-1", phone = "01012345678")
            .andExpect(jsonPath("$.traineeId").value(id))
            .andExpect(jsonPath("$.created").value(false))
        count() shouldBe 1
    }

    @Test
    fun `다른 박람회의 같은 번호는 새로 만든다`() {
        resolveOrCreate(trainingId = "T-1", phone = PHONE).andExpect(jsonPath("$.created").value(true))

        resolveOrCreate(expoId = OTHER_EXPO, trainingId = "T-1", phone = PHONE).andExpect(jsonPath("$.created").value(true))
        count() shouldBe 2
    }

    @Test
    fun `연수 번호는 겹쳐도 거부하지 않는다`() {
        val first = resolveOrCreate(trainingId = "T-1", phone = PHONE).andReturn().traineeId()

        // 같은 번호에 다른 연수 번호: 기존 연수자를 쓴다
        resolveOrCreate(trainingId = "T-2", phone = PHONE).andExpect(jsonPath("$.traineeId").value(first))
        // 다른 번호에 같은 연수 번호: v1처럼 새로 만든다
        resolveOrCreate(trainingId = "T-1", phone = "01099998888").andExpect(jsonPath("$.created").value(true))
        count() shouldBe 2
    }

    @Test
    fun `같은 연수 번호가 둘 생기면 연수 번호로 찾는 resolve는 409로 실패한다`() {
        // 연수 번호 중복을 허용하는 정책(v1)의 영향을 고정한다. 이 정책을 바꾸면 이 테스트도 함께 바꾼다
        resolveOrCreate(trainingId = "T-1", phone = PHONE).andExpect(jsonPath("$.created").value(true))
        mockMvc
            .perform(
                post("/internal/trainees/resolve")
                    .header("X-Internal-Token", INTERNAL_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"expoId":"$EXPO","trainingId":"T-1"}"""),
            ).andExpect(status().isOk)

        resolveOrCreate(trainingId = "T-1", phone = "01099998888").andExpect(jsonPath("$.created").value(true))
        mockMvc
            .perform(
                post("/internal/trainees/resolve")
                    .header("X-Internal-Token", INTERNAL_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"expoId":"$EXPO","trainingId":"T-1"}"""),
            ).andExpect(status().isConflict)
    }

    @Test
    fun `같은 번호의 동시 요청은 하나만 만들고 모두 같은 traineeId를 받는다`() {
        val start = CountDownLatch(1)
        val futures =
            List(THREADS) { index ->
                executor.submit<MvcResult> {
                    start.await()
                    resolveOrCreate(trainingId = "T-1", phone = if (index % 2 == 0) "010-1234-5678" else "01012345678").andReturn()
                }
            }
        start.countDown()
        val results = futures.map { it.get(30, TimeUnit.SECONDS) }

        results.all { it.response.status == 200 } shouldBe true
        results.map { it.traineeId() }.toSet().size shouldBe 1
        results.count { it.response.contentAsString.contains("\"created\":true") } shouldBe 1
        count() shouldBe 1
    }

    @Test
    fun `삭제 중이거나 삭제된 박람회에는 만들 수 없고 409이다`() {
        jdbcTemplate.update("INSERT INTO tb_expo_deletion (expo_id, started_at) VALUES (?, now())", EXPO)

        resolveOrCreate(trainingId = "T-1", phone = PHONE).andExpect(status().isConflict)
        count() shouldBe 0
    }

    @Test
    fun `소속 학교는 만들 때만 저장하고 재사용할 때는 바꾸지 않는다`() {
        resolveOrCreate(trainingId = "T-1", phone = PHONE, school = "빛고을초등학교").andExpect(jsonPath("$.created").value(true))
        jdbcTemplate.queryForObject("SELECT school FROM tb_trainee", String::class.java) shouldBe "빛고을초등학교"

        resolveOrCreate(trainingId = "T-1", phone = PHONE, school = "다른학교").andExpect(jsonPath("$.created").value(false))
        jdbcTemplate.queryForObject("SELECT school FROM tb_trainee", String::class.java) shouldBe "빛고을초등학교"

        resolveOrCreate(trainingId = "T-2", phone = "01099998888").andExpect(jsonPath("$.created").value(true))
        jdbcTemplate.queryForObject("SELECT school FROM tb_trainee WHERE phone_number = '01099998888'", String::class.java) shouldBe null
    }

    @Test
    fun `삭제 기록이 있는 박람회는 기존 연수자도 돌려주지 않고 409이다`() {
        resolveOrCreate(trainingId = "T-1", phone = PHONE).andExpect(jsonPath("$.created").value(true))
        jdbcTemplate.update("INSERT INTO tb_expo_deletion (expo_id, started_at) VALUES (?, now())", EXPO)

        resolveOrCreate(trainingId = "T-1", phone = PHONE).andExpect(status().isConflict)
    }

    @Test
    fun `삭제가 커밋되기 전에 들어온 요청은 기다렸다가 기존 연수자를 돌려주지 않고 409이다`() {
        resolveOrCreate(trainingId = "T-1", phone = PHONE).andExpect(jsonPath("$.created").value(true))
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val deleting =
            executor.submit {
                transactionTemplate.executeWithoutResult {
                    // 삭제 트랜잭션처럼 배타 lock을 잡고 연수자를 지운 뒤 아직 커밋하지 않는다
                    jdbcTemplate.query("SELECT pg_advisory_xact_lock(?, hashtext(?))", { _ -> }, ExpoDeletionLock.NAMESPACE, EXPO)
                    jdbcTemplate.update("INSERT INTO tb_expo_deletion (expo_id, started_at) VALUES (?, now())", EXPO)
                    jdbcTemplate.update("DELETE FROM tb_trainee WHERE expo_id = ?", EXPO)
                    locked.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
            }
        locked.await(10, TimeUnit.SECONDS) shouldBe true

        val resolving = executor.submit<Int> { resolveOrCreate(trainingId = "T-1", phone = PHONE).andReturn().response.status }
        Thread.sleep(BLOCK_CHECK_MILLIS)
        // 지워진 행을 읽어 곧 사라질 ID를 돌려주지 않고 삭제가 끝나길 기다린다
        resolving.isDone shouldBe false

        release.countDown()
        deleting.get(10, TimeUnit.SECONDS)
        resolving.get(10, TimeUnit.SECONDS) shouldBe 409
        count() shouldBe 0
    }

    @Test
    fun `잘못된 요청은 400이고 저장하지 않는다`() {
        resolveOrCreate(trainingId = "", phone = PHONE).andExpect(status().isBadRequest)
        resolveOrCreate(trainingId = "T-1", phone = "전화번호아님").andExpect(status().isBadRequest)
        resolveOrCreate(trainingId = "T-1", phone = PHONE, name = "열글자를넘는이름입니다").andExpect(status().isBadRequest)
        resolveOrCreate(trainingId = "T-1", phone = PHONE, informationJson = "{깨진 json").andExpect(status().isBadRequest)
        send("""{"expoId":"$EXPO"}""").andExpect(status().isBadRequest)

        count() shouldBe 0
    }

    @Test
    fun `토큰이 없거나 틀리거나 관리자 토큰만 있으면 401이고 만들지 않는다`() {
        val body = body(EXPO, "T-1", PHONE, "연수자", "{\"1\":\"a\"}", true)
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized)
        mockMvc
            .perform(post(PATH).header("X-Internal-Token", "wrong").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)
        mockMvc
            .perform(post(PATH).header("Authorization", bearerOf(1L)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)

        count() shouldBe 0
    }

    private fun resolveOrCreate(
        expoId: String = EXPO,
        trainingId: String,
        phone: String,
        name: String = "연수자",
        informationJson: String? = "{\"1\":\"a\"}",
        personalInformationStatus: Boolean = true,
        school: String? = null,
    ): ResultActions = send(body(expoId, trainingId, phone, name, informationJson, personalInformationStatus, school))

    private fun send(body: String): ResultActions =
        mockMvc.perform(post(PATH).header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun body(
        expoId: String,
        trainingId: String,
        phone: String,
        name: String,
        informationJson: String?,
        personalInformationStatus: Boolean,
        school: String? = null,
    ): String {
        val info = json(informationJson)
        val schoolJson = json(school)
        return """{"expoId":"$expoId","trainingId":"$trainingId","name":"$name","phoneNumber":"$phone","informationJson":$info,""" +
            """"personalInformationStatus":$personalInformationStatus,"school":$schoolJson}"""
    }

    private fun json(value: String?) = if (value == null) "null" else "\"" + value.replace("\"", "\\\"") + "\""

    private fun MvcResult.traineeId(): Long =
        response.contentAsString
            .substringAfter("\"traineeId\":")
            .substringBefore(',')
            .toLong()

    private fun count() = jdbcTemplate.queryForObject("SELECT count(*) FROM tb_trainee", Long::class.java)

    private companion object {
        const val PATH = "/internal/trainees/resolve-or-create"
        const val EXPO = "0199aaaa-0000-7000-8000-0000000000c1"
        const val OTHER_EXPO = "0199aaaa-0000-7000-8000-0000000000c2"
        const val PHONE = "01012345678"
        const val THREADS = 8
        const val BLOCK_CHECK_MILLIS = 500L
    }
}
