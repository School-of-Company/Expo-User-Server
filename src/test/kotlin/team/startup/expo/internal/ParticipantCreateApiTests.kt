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
import team.startup.expo.support.IntegrationTestSupport
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ParticipantCreateApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    private val executor = Executors.newFixedThreadPool(THREADS)

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_expo_deletion, tb_trainee, tb_standard_participant RESTART IDENTITY CASCADE")
    }

    @AfterEach
    fun tearDown() {
        executor.shutdownNow()
    }

    // --- POST /internal/standard-participants

    @Test
    fun `새 일반 참가자는 201이고 직업과 소속 학교와 발송 횟수 0으로 저장한다`() {
        createStandard(phone = PHONE, occupation = "TEACHER", school = "광주소프트웨어마이스터고")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.participantId").isNumber)
            .andExpect(jsonPath("$.phoneNumber").value(PHONE))
            .andExpect(jsonPath("$.created").doesNotExist())

        val row = jdbcTemplate.queryForMap("SELECT * FROM tb_standard_participant WHERE expo_id = ?", EXPO)
        row["occupation"] shouldBe "TEACHER"
        row["school"] shouldBe "광주소프트웨어마이스터고"
        row["sms_try_time"] shouldBe 0
        row["application_type"] shouldBe "PRE"
    }

    @Test
    fun `직업과 소속 학교는 없어도 저장하고 null이다`() {
        createStandard(phone = PHONE).andExpect(status().isCreated)

        val row = jdbcTemplate.queryForMap("SELECT occupation, school FROM tb_standard_participant WHERE expo_id = ?", EXPO)
        row["occupation"] shouldBe null
        row["school"] shouldBe null
    }

    @Test
    fun `이미 있고 발송 횟수가 2 미만이면 새로 만들지 않고 기존 참가자로 200이다`() {
        val id = createStandard(phone = PHONE).andExpect(status().isCreated).andReturn().participantId()
        smsTry("STANDARD", PHONE).andExpect(status().isNoContent)

        createStandard(phone = PHONE, name = "다른이름")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participantId").value(id))

        count("tb_standard_participant") shouldBe 1
        jdbcTemplate.queryForObject("SELECT name FROM tb_standard_participant", String::class.java) shouldBe "홍길동"
    }

    @Test
    fun `발송 횟수가 2 이상이면 409이다`() {
        createStandard(phone = PHONE).andExpect(status().isCreated)
        repeat(2) { smsTry("STANDARD", PHONE).andExpect(status().isNoContent) }

        createStandard(phone = PHONE).andExpect(status().isConflict)
    }

    @Test
    fun `하이픈 표기가 달라도 같은 번호로 보고 중복 행을 만들지 않는다`() {
        val id = createStandard(phone = "010-1234-5678").andExpect(status().isCreated).andReturn().participantId()

        createStandard(phone = "01012345678")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participantId").value(id))
        count("tb_standard_participant") shouldBe 1
    }

    @Test
    fun `다른 박람회의 같은 번호는 별개로 만든다`() {
        createStandard(phone = PHONE).andExpect(status().isCreated)
        createStandard(expoId = OTHER_EXPO, phone = PHONE).andExpect(status().isCreated)

        count("tb_standard_participant") shouldBe 2
    }

    @Test
    fun `같은 번호의 동시 요청은 500 없이 한 행만 남긴다`() {
        val statuses = concurrently { createStandard(phone = PHONE).andReturn().response.status }

        statuses.all { it in listOf(201, 200, 409) } shouldBe true
        statuses.count { it == 201 } shouldBe 1
        count("tb_standard_participant") shouldBe 1
    }

    @Test
    fun `표기만 다른 같은 번호의 동시 요청도 한 행만 만든다`() {
        val statuses =
            concurrentlyIndexed { index ->
                createStandard(phone = if (index % 2 == 0) "010-1234-5678" else "01012345678").andReturn().response.status
            }

        statuses.count { it == 201 } shouldBe 1
        statuses.count { it == 200 } shouldBe THREADS - 1
        count("tb_standard_participant") shouldBe 1
    }

    @Test
    fun `제출 당시 폼 스냅샷을 formId와 함께 저장하고 없으면 null이다`() {
        createStandard(phone = PHONE, formId = FORM_ID, questions = QUESTIONS).andExpect(status().isCreated)
        createStandard(phone = "01099998888").andExpect(status().isCreated)

        val withSnapshot = jdbcTemplate.queryForMap("SELECT * FROM tb_standard_participant WHERE phone_number = ?", PHONE)
        withSnapshot["information_form_id"] shouldBe FORM_ID
        firstQuestionTitle("tb_standard_participant WHERE phone_number = '$PHONE'") shouldBe "이름"
        // information_json(문항 제목 키 답변)은 그대로이고 스냅샷은 별도 컬럼이다
        jdbcTemplate.queryForObject(
            "SELECT information_json ->> '1' FROM tb_standard_participant WHERE phone_number = '$PHONE'",
            String::class.java,
        ) shouldBe
            "a"
        val without = jdbcTemplate.queryForMap("SELECT * FROM tb_standard_participant WHERE phone_number = ?", "01099998888")
        without["information_form_id"] shouldBe null
        without["information_questions"] shouldBe null
    }

    @Test
    fun `이미 있는 일반 참가자를 재사용할 때는 스냅샷을 덮어쓰지 않는다`() {
        createStandard(phone = PHONE, formId = FORM_ID, questions = QUESTIONS).andExpect(status().isCreated)

        createStandard(phone = PHONE, formId = "다른폼", questions = """[{"id":"9","title":"바뀜"}]""").andExpect(status().isOk)

        jdbcTemplate.queryForObject("SELECT information_form_id FROM tb_standard_participant", String::class.java) shouldBe FORM_ID
        jdbcTemplate.queryForObject(
            "SELECT information_questions -> 0 ->> 'title' FROM tb_standard_participant",
            String::class.java,
        ) shouldBe
            "이름"
    }

    @Test
    fun `questions가 배열이 아니거나 객체가 아닌 문항이 있으면 400이고 저장하지 않는다`() {
        createStandard(phone = PHONE, questions = """{"id":"1","title":"이름"}""").andExpect(status().isBadRequest)
        createStandard(phone = PHONE, questions = """["이름"]""").andExpect(status().isBadRequest)
        createTrainee(training = "T-1", phone = PHONE, questions = """{"id":"1"}""").andExpect(status().isBadRequest)

        count("tb_standard_participant") shouldBe 0
        count("tb_trainee") shouldBe 0
    }

    @Test
    fun `연수자도 제출 당시 폼 스냅샷을 저장한다`() {
        createTrainee(training = "T-1", phone = PHONE, formId = FORM_ID, questions = QUESTIONS).andExpect(status().isCreated)

        formIdOf("tb_trainee") shouldBe FORM_ID
        firstQuestionTitle("tb_trainee") shouldBe "이름"
    }

    @Test
    fun `삭제 중이거나 삭제된 박람회에는 만들 수 없고 409이다`() {
        jdbcTemplate.update("INSERT INTO tb_expo_deletion (expo_id, started_at) VALUES (?, now())", EXPO)

        createStandard(phone = PHONE).andExpect(status().isConflict)
        count("tb_standard_participant") shouldBe 0
    }

    @Test
    fun `잘못된 요청은 400이고 저장하지 않는다`() {
        createStandard(phone = PHONE, name = "열글자를넘는이름입니다").andExpect(status().isBadRequest)
        createStandard(phone = "전화번호아님").andExpect(status().isBadRequest)
        createStandard(phone = PHONE, informationJson = "{깨진 json").andExpect(status().isBadRequest)
        createStandard(phone = PHONE, occupation = "ASTRONAUT").andExpect(status().isBadRequest)
        post("/internal/standard-participants", """{"expoId":"$EXPO"}""").andExpect(status().isBadRequest)

        count("tb_standard_participant") shouldBe 0
    }

    // --- POST /internal/trainees

    @Test
    fun `새 연수자는 201이고 소속 학교를 저장한다`() {
        createTrainee(training = "T-1", phone = PHONE, school = "광주소프트웨어마이스터고")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.traineeId").isNumber)
            .andExpect(jsonPath("$.phoneNumber").value(PHONE))

        val row = jdbcTemplate.queryForMap("SELECT * FROM tb_trainee WHERE expo_id = ?", EXPO)
        row["school"] shouldBe "광주소프트웨어마이스터고"
        row["application_type"] shouldBe "PRE"
    }

    @Test
    fun `사전 등록은 연수 번호가 같으면 전화번호가 달라도 409이다`() {
        createTrainee(training = "T-1", phone = PHONE).andExpect(status().isCreated)

        createTrainee(training = "T-1", phone = "01099998888").andExpect(status().isConflict)
        count("tb_trainee") shouldBe 1
    }

    @Test
    fun `사전 등록은 전화번호가 같으면 연수 번호가 달라도 409이다`() {
        createTrainee(training = "T-1", phone = PHONE).andExpect(status().isCreated)

        createTrainee(training = "T-2", phone = PHONE).andExpect(status().isConflict)
        createTrainee(training = "T-2", phone = "010-1234-5678").andExpect(status().isConflict)
        count("tb_trainee") shouldBe 1
    }

    @Test
    fun `사전 등록은 같은 번호의 일반 참가자가 있어도 만들 수 있다`() {
        createStandard(phone = PHONE).andExpect(status().isCreated)

        createTrainee(training = "T-1", phone = PHONE).andExpect(status().isCreated)
    }

    @Test
    fun `현장 등록은 FIELD로 저장한다`() {
        createTrainee(training = "T-1", phone = PHONE, applicationType = "FIELD").andExpect(status().isCreated)

        jdbcTemplate.queryForObject("SELECT application_type FROM tb_trainee", String::class.java) shouldBe "FIELD"
    }

    @Test
    fun `현장 등록은 같은 번호의 일반 참가자가 있으면 409이다`() {
        createStandard(phone = PHONE).andExpect(status().isCreated)

        createTrainee(training = "T-1", phone = PHONE, applicationType = "FIELD").andExpect(status().isConflict)
        count("tb_trainee") shouldBe 0
    }

    @Test
    fun `현장 등록은 같은 번호의 연수자가 있으면 409이다`() {
        createTrainee(training = "T-1", phone = PHONE).andExpect(status().isCreated)

        createTrainee(training = "T-2", phone = PHONE, applicationType = "FIELD").andExpect(status().isConflict)
        count("tb_trainee") shouldBe 1
    }

    @Test
    fun `현장 등록은 연수 번호가 같아도 번호가 다르면 만들 수 있다`() {
        createTrainee(training = "T-1", phone = PHONE).andExpect(status().isCreated)

        createTrainee(training = "T-1", phone = "01099998888", applicationType = "FIELD").andExpect(status().isCreated)
    }

    @Test
    fun `같은 번호의 동시 요청은 하나만 201이고 나머지는 409이다`() {
        val statuses = concurrently { createTrainee(training = "T-1", phone = PHONE).andReturn().response.status }

        statuses.count { it == 201 } shouldBe 1
        statuses.count { it == 409 } shouldBe THREADS - 1
        count("tb_trainee") shouldBe 1
    }

    @Test
    fun `사전 등록은 같은 연수 번호를 다른 번호로 동시에 신청해도 하나만 만든다`() {
        val statuses =
            concurrentlyIndexed { index ->
                createTrainee(training = "T-1", phone = "0102222000$index").andReturn().response.status
            }

        statuses.count { it == 201 } shouldBe 1
        statuses.count { it == 409 } shouldBe THREADS - 1
        count("tb_trainee") shouldBe 1
    }

    @Test
    fun `표기만 다른 같은 번호를 연수자로 동시에 등록해도 하나만 만든다`() {
        val statuses =
            concurrentlyIndexed { index ->
                createTrainee(
                    training = "T-$index",
                    phone =
                        if (index % 2 ==
                            0
                        ) {
                            "010-1234-5678"
                        } else {
                            "01012345678"
                        },
                ).andReturn().response.status
            }

        statuses.count { it == 201 } shouldBe 1
        statuses.count { it == 409 } shouldBe THREADS - 1
        count("tb_trainee") shouldBe 1
    }

    @Test
    fun `삭제 중이거나 삭제된 박람회에는 연수자를 만들 수 없고 409이다`() {
        jdbcTemplate.update("INSERT INTO tb_expo_deletion (expo_id, started_at) VALUES (?, now())", EXPO)

        createTrainee(training = "T-1", phone = PHONE).andExpect(status().isConflict)
        count("tb_trainee") shouldBe 0
    }

    @Test
    fun `연수자의 잘못된 요청은 400이다`() {
        createTrainee(training = "", phone = PHONE).andExpect(status().isBadRequest)
        createTrainee(training = "T-1", phone = PHONE, informationJson = "{깨진 json").andExpect(status().isBadRequest)
        createTrainee(training = "T-1", phone = PHONE, applicationType = "LATE").andExpect(status().isBadRequest)

        count("tb_trainee") shouldBe 0
    }

    // --- POST /internal/participants/sms-try

    @Test
    fun `발송 횟수를 1 올린다`() {
        createStandard(phone = PHONE).andExpect(status().isCreated)

        smsTry("STANDARD", PHONE).andExpect(status().isNoContent)
        smsTry("STANDARD", "010-1234-5678").andExpect(status().isNoContent)

        jdbcTemplate.queryForObject("SELECT sms_try_time FROM tb_standard_participant", Int::class.java) shouldBe 2
    }

    @Test
    fun `동시에 올려도 증가가 덮어써지지 않는다`() {
        createStandard(phone = PHONE).andExpect(status().isCreated)

        val statuses = concurrently { smsTry("STANDARD", PHONE).andReturn().response.status }

        statuses.all { it == 204 } shouldBe true
        jdbcTemplate.queryForObject("SELECT sms_try_time FROM tb_standard_participant", Int::class.java) shouldBe THREADS
    }

    @Test
    fun `참가자가 없거나 다른 박람회면 404이고 연수자는 400이다`() {
        smsTry("STANDARD", PHONE).andExpect(status().isNotFound)

        createStandard(phone = PHONE).andExpect(status().isCreated)
        smsTry("STANDARD", PHONE, expoId = OTHER_EXPO).andExpect(status().isNotFound)
        smsTry("TRAINEE", PHONE).andExpect(status().isBadRequest)
    }

    @Test
    fun `같은 eventId로 다시 불러도 횟수는 한 번만 올라가고 204이다`() {
        createStandard(phone = PHONE).andExpect(status().isCreated)
        val eventId = UUID.randomUUID().toString()

        repeat(3) { smsTry("STANDARD", PHONE, eventId = eventId).andExpect(status().isNoContent) }

        jdbcTemplate.queryForObject("SELECT sms_try_time FROM tb_standard_participant", Int::class.java) shouldBe 1
        count("tb_sms_try_event") shouldBe 1
    }

    @Test
    fun `같은 eventId를 동시에 불러도 횟수는 한 번만 올라간다`() {
        createStandard(phone = PHONE).andExpect(status().isCreated)
        val eventId = UUID.randomUUID().toString()

        val statuses = concurrently { smsTry("STANDARD", PHONE, eventId = eventId).andReturn().response.status }

        statuses.all { it == 204 } shouldBe true
        jdbcTemplate.queryForObject("SELECT sms_try_time FROM tb_standard_participant", Int::class.java) shouldBe 1
    }

    @Test
    fun `다른 참가자에 쓴 eventId는 409이고 횟수를 올리지 않는다`() {
        createStandard(phone = PHONE).andExpect(status().isCreated)
        createStandard(phone = "01099998888").andExpect(status().isCreated)
        val eventId = UUID.randomUUID().toString()
        smsTry("STANDARD", PHONE, eventId = eventId).andExpect(status().isNoContent)

        smsTry("STANDARD", "01099998888", eventId = eventId).andExpect(status().isConflict)

        jdbcTemplate.queryForObject("SELECT sum(sms_try_time) FROM tb_standard_participant", Int::class.java) shouldBe 1
    }

    @Test
    fun `eventId가 없거나 비어 있으면 400이다`() {
        createStandard(phone = PHONE).andExpect(status().isCreated)

        post("/internal/participants/sms-try", """{"expoId":"$EXPO","participationType":"STANDARD","phoneNumber":"$PHONE"}""")
            .andExpect(status().isBadRequest)
        smsTry("STANDARD", PHONE, eventId = "").andExpect(status().isBadRequest)

        jdbcTemplate.queryForObject("SELECT sms_try_time FROM tb_standard_participant", Int::class.java) shouldBe 0
    }

    // --- 인증

    @Test
    fun `토큰이 없거나 틀리거나 관리자 토큰만 있으면 401이고 아무것도 만들지 않는다`() {
        val requests =
            mapOf(
                "/internal/standard-participants" to standardBody(PHONE),
                "/internal/trainees" to traineeBody("T-1", PHONE),
                "/internal/participants/sms-try" to smsTryBody("STANDARD", PHONE, EXPO),
            )
        requests.forEach { (path, body) ->
            mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized)
            mockMvc
                .perform(post(path).header("X-Internal-Token", "wrong").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized)
            mockMvc
                .perform(post(path).header("Authorization", bearerOf(1L)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized)
        }

        count("tb_standard_participant") shouldBe 0
        count("tb_trainee") shouldBe 0
    }

    private fun <T> concurrently(request: () -> T): List<T> = concurrentlyIndexed { request() }

    private fun <T> concurrentlyIndexed(request: (Int) -> T): List<T> {
        val start = CountDownLatch(1)
        val futures =
            List(THREADS) { index ->
                executor.submit<T> {
                    start.await()
                    request(index)
                }
            }
        start.countDown()
        return futures.map { it.get(30, TimeUnit.SECONDS) }
    }

    private fun createStandard(
        expoId: String = EXPO,
        phone: String,
        name: String = "홍길동",
        informationJson: String? = "{\"1\":\"a\"}",
        occupation: String? = null,
        school: String? = null,
        formId: String? = null,
        questions: String? = null,
    ) = post("/internal/standard-participants", standardBody(phone, expoId, name, informationJson, occupation, school, formId, questions))

    private fun createTrainee(
        training: String,
        phone: String,
        applicationType: String = "PRE",
        informationJson: String? = "{\"1\":\"a\"}",
        school: String? = null,
        formId: String? = null,
        questions: String? = null,
    ) = post("/internal/trainees", traineeBody(training, phone, applicationType, informationJson, school, formId, questions))

    private fun smsTry(
        type: String,
        phone: String,
        expoId: String = EXPO,
        eventId: String = UUID.randomUUID().toString(),
    ) = post("/internal/participants/sms-try", smsTryBody(type, phone, expoId, eventId))

    private fun post(
        path: String,
        body: String,
    ): ResultActions =
        mockMvc.perform(post(path).header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun MvcResult.participantId(): Long =
        response.contentAsString
            .substringAfter("\"participantId\":")
            .substringBefore(',')
            .toLong()

    private fun formIdOf(table: String) = jdbcTemplate.queryForObject("SELECT information_form_id FROM $table", String::class.java)

    /** [from]은 `FROM` 뒤의 테이블과 조건이다. */
    private fun firstQuestionTitle(from: String) =
        jdbcTemplate.queryForObject("SELECT information_questions -> 0 ->> 'title' FROM $from", String::class.java)

    private fun count(table: String) = jdbcTemplate.queryForObject("SELECT count(*) FROM $table", Long::class.java)

    private fun standardBody(
        phone: String,
        expoId: String = EXPO,
        name: String = "홍길동",
        informationJson: String? = "{\"1\":\"a\"}",
        occupation: String? = null,
        school: String? = null,
        formId: String? = null,
        questions: String? = null,
    ) = """{"expoId":"$expoId","name":"$name","phoneNumber":"$phone","informationJson":${json(informationJson)},""" +
        """"personalInformationStatus":true,"applicationType":"PRE","occupation":${json(occupation)},"school":${json(school)},""" +
        """"formId":${json(formId)},"questions":${questions ?: "null"}}"""

    private fun traineeBody(
        training: String,
        phone: String,
        applicationType: String = "PRE",
        informationJson: String? = "{\"1\":\"a\"}",
        school: String? = null,
        formId: String? = null,
        questions: String? = null,
    ) = """{"expoId":"$EXPO","trainingId":"$training","name":"연수자","phoneNumber":"$phone","informationJson":${json(informationJson)},""" +
        """"personalInformationStatus":true,"applicationType":"$applicationType","school":${json(school)},""" +
        """"formId":${json(formId)},"questions":${questions ?: "null"}}"""

    private fun smsTryBody(
        type: String,
        phone: String,
        expoId: String,
        eventId: String = UUID.randomUUID().toString(),
    ) = """{"expoId":"$expoId","participationType":"$type","phoneNumber":"$phone","eventId":"$eventId"}"""

    // 문자열 값은 따옴표로 감싸고 null은 null로 쓴다
    private fun json(value: String?) = if (value == null) "null" else "\"" + value.replace("\"", "\\\"") + "\""

    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-0000000000a1"
        const val OTHER_EXPO = "0199aaaa-0000-7000-8000-0000000000a2"
        const val PHONE = "01012345678"
        const val FORM_ID = "0199aaaa-0000-7000-8000-0000000000f1"
        const val QUESTIONS = """[{"id":"1","title":"이름","order":0}]"""
        const val THREADS = 8
    }
}
