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
import team.startup.expo.support.IntegrationTestSupport
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TraineeParticipantLinkApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var standardParticipantRepository: StandardParticipantRepository

    private val executor = Executors.newFixedThreadPool(THREADS)

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_expo_deletion, tb_trainee, tb_standard_participant RESTART IDENTITY CASCADE")
    }

    @AfterEach
    fun tearDown() {
        executor.shutdownNow()
    }

    // --- POST /internal/trainees/resolve-or-create-by-participant

    @Test
    fun `참가자에 이어진 연수자가 없으면 한 번 만들고 번호 없이 참가자의 정보와 문자 번호를 갖는다`() {
        val representative = participant("대표", PHONE, school = "광주고")

        link(EXPO, representative, trainingId = "T-1")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.traineeId").isNumber)
            .andExpect(jsonPath("$.created").value(true))

        val row = jdbcTemplate.queryForMap("SELECT * FROM tb_trainee")
        row["standard_participant_id"] shouldBe representative
        row["expo_id"] shouldBe EXPO
        row["name"] shouldBe "대표"
        row["phone_number"] shouldBe null
        row["notification_phone_number"] shouldBe PHONE
        row["training_id"] shouldBe "T-1"
        row["school"] shouldBe "광주고"
        row["personal_information_status"] shouldBe true
    }

    @Test
    fun `동행자의 문자 번호는 대표자 번호이고 연수 번호는 비워 둘 수 있다`() {
        val representative = participant("대표", PHONE)
        val companion = participant("동행", null, representativeId = representative)

        link(EXPO, companion).andExpect(status().isOk).andExpect(jsonPath("$.created").value(true))

        val row = jdbcTemplate.queryForMap("SELECT * FROM tb_trainee")
        row["phone_number"] shouldBe null
        row["training_id"] shouldBe null
        row["notification_phone_number"] shouldBe PHONE
    }

    @Test
    fun `같은 수신 번호를 쓰는 대표자와 동행자들도 서로 다른 연수자를 갖는다`() {
        val representative = participant("대표", PHONE)
        val first = participant("동행1", null, representativeId = representative)
        val second = participant("동행2", null, representativeId = representative)

        val ids = listOf(representative, first, second).map { traineeIdOf(link(EXPO, it).andExpect(status().isOk)) }

        ids.toSet().size shouldBe 3
        count("tb_trainee") shouldBe 3
        jdbcTemplate
            .queryForList("SELECT notification_phone_number FROM tb_trainee", String::class.java)
            .toSet() shouldBe setOf(PHONE)
    }

    @Test
    fun `같은 참가자로 다시 부르면 같은 연수자를 돌려주고 요청 값으로 덮어쓰지 않는다`() {
        val representative = participant("대표", PHONE)
        val first = traineeIdOf(link(EXPO, representative, trainingId = "T-1", school = "광주고").andExpect(status().isOk))

        link(EXPO, representative, trainingId = "T-2", school = "다른학교")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.traineeId").value(first))
            .andExpect(jsonPath("$.created").value(false))

        count("tb_trainee") shouldBe 1
        val row = jdbcTemplate.queryForMap("SELECT training_id, school FROM tb_trainee")
        row["training_id"] shouldBe "T-1"
        row["school"] shouldBe "광주고"
    }

    @Test
    fun `동시에 처음 부르면 연수자를 하나만 만든다`() {
        val representative = participant("대표", PHONE)

        val results = concurrently { link(EXPO, representative).andReturn().response.contentAsString }

        count("tb_trainee") shouldBe 1
        results.count { it.contains("\"created\":true") } shouldBe 1
        results.map { it.substringAfter("\"traineeId\":").substringBefore(',') }.toSet().size shouldBe 1
    }

    @Test
    fun `없는 참가자와 다른 박람회의 참가자는 404이고 연수자를 만들지 않는다`() {
        val other = participant("다른행사", "01099990000", expoId = OTHER_EXPO)

        link(EXPO, other).andExpect(status().isNotFound)
        link(EXPO, other + 1000).andExpect(status().isNotFound)

        count("tb_trainee") shouldBe 0
    }

    @Test
    fun `삭제 중이거나 삭제된 박람회에는 연수자를 이을 수 없고 409이다`() {
        val representative = participant("대표", PHONE)
        jdbcTemplate.update("INSERT INTO tb_expo_deletion (expo_id, started_at) VALUES (?, now())", EXPO)

        link(EXPO, representative).andExpect(status().isConflict)

        count("tb_trainee") shouldBe 0
    }

    @Test
    fun `잘못된 요청은 400이다`() {
        invalidLink("""{"expoId":"$EXPO"}""")
        invalidLink("""{"expoId":"$EXPO","participantId":0}""")
        invalidLink("""{"expoId":"$EXPO","participantId":1,"trainingId":"1234567890123456"}""")
        invalidLink("""{"expoId":"$EXPO","participantId":1,"trainingId":""}""")
    }

    // --- POST /internal/standard-participants/trainee

    @Test
    fun `참가자 ID와 code가 맞고 이어진 연수자가 있으면 연수자 ID를 돌려준다`() {
        val representative = participant("대표", PHONE)
        val traineeId = traineeIdOf(link(EXPO, representative).andExpect(status().isOk))

        lookup(EXPO, representative, codeOf(representative))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.traineeId").value(traineeId))
    }

    @Test
    fun `code가 다르거나 다른 박람회이거나 없는 참가자이거나 연결이 없으면 모두 같은 404이고 아무것도 만들지 않는다`() {
        val linked = participant("대표", PHONE)
        link(EXPO, linked).andExpect(status().isOk)
        val unlinked = participant("연결없음", "01077778888")
        val before = count("tb_trainee")

        lookup(EXPO, linked, "x".repeat(22)).andExpect(status().isNotFound)
        lookup(OTHER_EXPO, linked, codeOf(linked)).andExpect(status().isNotFound)
        lookup(EXPO, linked + 1000, codeOf(linked)).andExpect(status().isNotFound)
        lookup(EXPO, unlinked, codeOf(unlinked)).andExpect(status().isNotFound)

        count("tb_trainee") shouldBe before
        count("tb_standard_participant_participation") shouldBe 0
        count("tb_trainee_participation") shouldBe 0
    }

    @Test
    fun `조회는 필수 값이 없으면 400이다`() {
        post("/internal/standard-participants/trainee", """{"expoId":"$EXPO","code":"abc"}""").andExpect(status().isBadRequest)
        post("/internal/standard-participants/trainee", """{"expoId":"$EXPO","participantId":1}""").andExpect(status().isBadRequest)
    }

    // --- 호환

    @Test
    fun `이어진 연수자는 번호가 없어 번호로 연수자를 찾는 기존 경로에 섞이지 않고 번호만으로 합쳐지지 않는다`() {
        val representative = participant("대표", PHONE)
        val linkedId = traineeIdOf(link(EXPO, representative).andExpect(status().isOk))

        val body =
            post(
                "/internal/trainees/resolve-or-create",
                """{"expoId":"$EXPO","trainingId":"T-9","name":"번호연수자","phoneNumber":"$PHONE","personalInformationStatus":true}""",
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.created").value(true))
                .andReturn()
                .response.contentAsString

        val phoneTraineeId = body.substringAfter("\"traineeId\":").substringBefore(',').toLong()
        (phoneTraineeId == linkedId) shouldBe false
        count("tb_trainee") shouldBe 2
    }

    @Test
    fun `번호와 연수 번호가 없는 연수자도 상세와 이름 조회가 되고 문자 번호를 돌려준다`() {
        val representative = participant("대표", PHONE)
        val companion = participant("동행", null, representativeId = representative)
        val traineeId = traineeIdOf(link(EXPO, companion).andExpect(status().isOk))

        mockMvc
            .perform(get("/internal/trainees/$traineeId/details").header("X-Internal-Token", INTERNAL_TOKEN))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.traineeId").value(traineeId))
            .andExpect(jsonPath("$.trainingId").doesNotExist())
        post("/internal/trainees/names", """{"expoId":"$EXPO","traineeIds":[$traineeId]}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("동행"))
    }

    @Test
    fun `박람회를 삭제하면 이어진 연수자도 함께 지운다`() {
        val representative = participant("대표", PHONE)
        link(EXPO, representative).andExpect(status().isOk)

        mockMvc.perform(delete("/internal/expos/$EXPO").header("X-Internal-Token", INTERNAL_TOKEN)).andExpect(status().isNoContent)

        count("tb_trainee") shouldBe 0
        count("tb_standard_participant") shouldBe 0
    }

    // --- 인증

    @Test
    fun `토큰이 없거나 틀리거나 관리자 토큰만 있으면 두 경로 모두 401이고 아무것도 만들지 않는다`() {
        val representative = participant("대표", PHONE)
        val requests =
            mapOf(
                "/internal/trainees/resolve-or-create-by-participant" to """{"expoId":"$EXPO","participantId":$representative}""",
                "/internal/standard-participants/trainee" to """{"expoId":"$EXPO","participantId":$representative,"code":"abc"}""",
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

        count("tb_trainee") shouldBe 0
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

    private fun link(
        expoId: String,
        participantId: Long,
        trainingId: String? = null,
        school: String? = null,
    ): ResultActions {
        val fields =
            listOfNotNull(
                "\"expoId\":\"$expoId\"",
                "\"participantId\":$participantId",
                trainingId?.let { "\"trainingId\":\"$it\"" },
                school?.let { "\"school\":\"$it\"" },
            )
        return post("/internal/trainees/resolve-or-create-by-participant", "{${fields.joinToString(",")}}")
    }

    private fun invalidLink(body: String) {
        post("/internal/trainees/resolve-or-create-by-participant", body).andExpect(status().isBadRequest)
    }

    private fun lookup(
        expoId: String,
        participantId: Long,
        code: String,
    ): ResultActions =
        post("/internal/standard-participants/trainee", """{"expoId":"$expoId","participantId":$participantId,"code":"$code"}""")

    private fun post(
        path: String,
        body: String,
    ): ResultActions =
        mockMvc.perform(post(path).header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun traineeIdOf(result: ResultActions): Long =
        result
            .andReturn()
            .response.contentAsString
            .substringAfter("\"traineeId\":")
            .substringBefore(',')
            .toLong()

    private fun codeOf(participantId: Long): String =
        jdbcTemplate.queryForObject("SELECT code FROM tb_standard_participant WHERE id = ?", String::class.java, participantId)!!

    private fun count(table: String) = jdbcTemplate.queryForObject("SELECT count(*) FROM $table", Long::class.java)

    private fun participant(
        name: String,
        phone: String?,
        representativeId: Long? = null,
        expoId: String = EXPO,
        school: String? = null,
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
                    school = school,
                    representativeId = representativeId,
                ),
            ).id!!

    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-000000000a01"
        const val OTHER_EXPO = "0199aaaa-0000-7000-8000-000000000a02"
        const val PHONE = "01012345678"
        const val THREADS = 8
    }
}
