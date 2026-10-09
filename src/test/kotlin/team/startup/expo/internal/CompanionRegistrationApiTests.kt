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
import team.startup.expo.support.IntegrationTestSupport
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

/** 동행자를 개별 참가자로 등록하고, 문자에 담을 참가자 목록과 입장 기록(`participantId` + `code`)을 확인한다. */
class CompanionRegistrationApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    private val mapper = JsonMapper.builder().build()

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE tb_expo_deletion, tb_standard_participant_participation, tb_registration_request, tb_registration_outbox, " +
                "tb_trainee, tb_standard_participant RESTART IDENTITY CASCADE",
        )
    }

    @Test
    fun `처음 신청하면 대표자와 동행자를 각각 만들고 문자에 전원을 담는다`() {
        register(ALICE, BOB, CAROL).andExpect(status().isCreated)

        val rows = jdbcTemplate.queryForList("SELECT * FROM tb_standard_participant ORDER BY id")
        rows.size shouldBe 3
        val representative = rows[0]
        representative["phone_number"] shouldBe PHONE
        representative["representative_id"] shouldBe null
        rows.drop(1).forEach {
            it["phone_number"] shouldBe null
            it["representative_id"] shouldBe representative["id"]
            it["region"] shouldBe "GWANGJU"
        }
        rows.map { it["code"] as String }.toSet().size shouldBe 3
        rows.all { (it["code"] as String).length == 22 } shouldBe true

        val outbox =
            jdbcTemplate.queryForMap(
                "SELECT participant_id, phone_number FROM tb_registration_outbox WHERE event_type = 'REGISTERED'",
            )
        outbox["participant_id"] shouldBe representative["id"]
        outbox["phone_number"] shouldBe PHONE
        val participants = registeredParticipants().single()
        participants.map { it.path("id").asLong() } shouldBe rows.map { it["id"] }
        participants.map { it.path("code").asString() } shouldBe rows.map { it["code"] }
        // 문자의 링크 라벨에 쓰는 이름은 대표자와 동행자 모두 싣는다
        participants.map { it.path("name").asString() } shouldBe listOf(ALICE.name, BOB.name, CAROL.name)
        count("tb_registration_outbox WHERE event_type = 'STANDARD_CREATED'") shouldBe 3
    }

    @Test
    fun `같은 번호로 같은 동행자를 다시 신청하면 사람을 만들지 않고 기존 전원의 문자를 다시 보낸다`() {
        register(ALICE, BOB).andExpect(status().isCreated)
        val codes = codes()

        register(ALICE, BOB).andExpect(status().isOk)

        count("tb_standard_participant") shouldBe 2
        codes() shouldBe codes
        val events = registeredParticipants()
        events.size shouldBe 2
        events.last().map { it.path("code").asString() } shouldBe codes
        events.last().map { it.path("name").asString() } shouldBe listOf(ALICE.name, BOB.name)
        count("tb_registration_outbox WHERE event_type = 'STANDARD_CREATED'") shouldBe 2
    }

    @Test
    fun `응답에 이번 문자에 담은 참가자 ID를 돌려주고 code는 싣지 않는다`() {
        val first = register(ALICE, BOB).andExpect(status().isCreated).andExpect(jsonPath("$.code").doesNotExist()).andReturn()
        val ids = jdbcTemplate.queryForList("SELECT id FROM tb_standard_participant ORDER BY id", Long::class.java)
        mapper
            .readTree(first.response.contentAsString)
            .path("participantIds")
            .toList()
            .map { it.asLong() } shouldBe ids

        // 새 동행자만 더하면 그 동행자만, 같은 요청을 다시 보내면 기존 전원이다
        val added = register(ALICE, BOB, CAROL).andReturn()
        mapper
            .readTree(added.response.contentAsString)
            .path("participantIds")
            .toList()
            .map { it.asLong() } shouldBe
            listOf(jdbcTemplate.queryForObject("SELECT id FROM tb_standard_participant WHERE name = '${CAROL.name}'", Long::class.java))
        val resent = register(ALICE, BOB, CAROL).andReturn()
        mapper
            .readTree(resent.response.contentAsString)
            .path("participantIds")
            .toList()
            .map { it.asLong() } shouldBe
            jdbcTemplate.queryForList("SELECT id FROM tb_standard_participant ORDER BY id", Long::class.java)
    }

    @Test
    fun `같은 requestId의 재시도는 처음의 참가자 ID 목록을 그대로 돌려준다`() {
        val requestId = UUID.randomUUID().toString()
        val first =
            register(ALICE, BOB, requestId = requestId)
                .andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString

        val retry =
            register(ALICE, BOB, requestId = requestId)
                .andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString

        mapper
            .readTree(retry)
            .path("participantIds")
            .toList()
            .map { it.asLong() } shouldBe
            mapper
                .readTree(first)
                .path("participantIds")
                .toList()
                .map { it.asLong() }
        mapper.readTree(first).path("participantIds").size() shouldBe 2
    }

    @Test
    fun `새 동행자만 만들고 그 동행자의 링크만 문자에 담는다`() {
        register(ALICE).andExpect(status().isCreated)
        val before = codes()

        register(ALICE, BOB).andExpect(status().isCreated)

        count("tb_standard_participant") shouldBe 2
        codes().take(1) shouldBe before
        val added = jdbcTemplate.queryForMap("SELECT id, code FROM tb_standard_participant WHERE name = '${BOB.name}'")
        val participants = registeredParticipants().last()
        participants.size shouldBe 1
        participants[0].path("id").asLong() shouldBe added["id"]
        participants[0].path("code").asString() shouldBe added["code"]
        participants[0].path("name").asString() shouldBe BOB.name
    }

    @Test
    fun `이름이 같아도 구분이나 소속이 다르면 다른 사람이다`() {
        register(ALICE, BOB).andExpect(status().isCreated)

        register(ALICE, BOB, BOB.copy(school = "다른학교")).andExpect(status().isCreated)

        count("tb_standard_participant WHERE name = '${BOB.name}'") shouldBe 2
    }

    @Test
    fun `대표자를 포함한 인원은 누적해서 5명을 넘을 수 없다`() {
        register(ALICE, BOB, CAROL, DAVE).andExpect(status().isCreated)

        register(ALICE, EVE, FRANK).andExpect(status().isConflict)

        count("tb_standard_participant") shouldBe 4
        count("tb_registration_outbox WHERE event_type = 'REGISTERED'") shouldBe 1
    }

    @Test
    fun `문자를 두 번 보낸 대표자는 동행자를 더하지 못한다`() {
        register(ALICE).andExpect(status().isCreated)
        repeat(2) { smsTry() }

        register(ALICE, BOB).andExpect(status().isConflict)

        count("tb_standard_participant") shouldBe 1
    }

    @Test
    fun `같은 requestId의 재시도는 동행자도 이벤트도 다시 만들지 않는다`() {
        val requestId = UUID.randomUUID().toString()
        register(ALICE, BOB, requestId = requestId).andExpect(status().isCreated)

        register(ALICE, BOB, requestId = requestId).andExpect(status().isCreated)

        count("tb_standard_participant") shouldBe 2
        registeredParticipants().size shouldBe 1
    }

    @Test
    fun `동행자는 participantId와 code로 입장하고 문자는 대표자 번호로 간다`() {
        register(ALICE, BOB).andExpect(status().isCreated)
        val companion = jdbcTemplate.queryForMap("SELECT id, code FROM tb_standard_participant WHERE name = '${BOB.name}'")

        enter(companion["id"] as Long, companion["code"] as String)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(companion["id"]))
            .andExpect(jsonPath("$.name").value(BOB.name))
            .andExpect(jsonPath("$.phoneNumber").doesNotExist())
            .andExpect(jsonPath("$.notificationPhoneNumber").value(PHONE))
            .andExpect(jsonPath("$.code").doesNotExist())

        count("tb_standard_participant_participation") shouldBe 1
    }

    @Test
    fun `code가 다르거나 없으면 404이고 입장을 기록하지 않는다`() {
        register(ALICE, BOB).andExpect(status().isCreated)
        val id = jdbcTemplate.queryForObject("SELECT id FROM tb_standard_participant WHERE name = '${BOB.name}'", Long::class.java)!!

        enter(id, "AAAAAAAAAAAAAAAAAAAAAA").andExpect(status().isNotFound)
        enter(id, null).andExpect(status().isNotFound)
        enter(id + 100, "AAAAAAAAAAAAAAAAAAAAAA").andExpect(status().isNotFound)

        count("tb_standard_participant_participation") shouldBe 0
    }

    @Test
    fun `대표자도 본인 번호가 있으면 번호로 입장할 수 있다`() {
        register(ALICE, BOB).andExpect(status().isCreated)

        post(
            "/internal/entries",
            """{"expoId":"$EXPO","participationType":"STANDARD","phoneNumber":"$PHONE"}""",
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value(ALICE.name))
            .andExpect(jsonPath("$.notificationPhoneNumber").value(PHONE))
    }

    @Test
    fun `번호도 code도 없는 입장 요청은 400이다`() {
        post("/internal/entries", """{"expoId":"$EXPO","participationType":"STANDARD"}""").andExpect(status().isBadRequest)
    }

    @Test
    fun `대표자를 지우면 동행자도 함께 지워진다`() {
        register(ALICE, BOB, CAROL).andExpect(status().isCreated)

        jdbcTemplate.update("DELETE FROM tb_standard_participant WHERE representative_id IS NULL")

        count("tb_standard_participant") shouldBe 0
    }

    private data class Person(
        val name: String,
        val occupation: String,
        val school: String? = null,
    )

    private fun register(
        representative: Person,
        vararg companions: Person,
        requestId: String? = null,
    ): ResultActions {
        val answers =
            mapOf(
                "동행자" to
                    companions.map { person ->
                        listOfNotNull(
                            "name" to person.name,
                            "occupation" to person.occupation,
                            "region" to "GWANGJU",
                            person.school?.let { "school" to it },
                        ).toMap()
                    },
            )
        val body =
            listOfNotNull(
                "expoId" to EXPO,
                "name" to representative.name,
                "phoneNumber" to PHONE,
                "informationJson" to mapper.writeValueAsString(answers),
                "personalInformationStatus" to true,
                "applicationType" to "PRE",
                "occupation" to representative.occupation,
                "questions" to listOf(mapOf("id" to "1", "title" to "동행자", "formType" to "COMPANION")),
                requestId?.let { "requestId" to it },
            ).toMap()
        return post("/internal/standard-participants", mapper.writeValueAsString(body))
    }

    private fun enter(
        participantId: Long,
        code: String?,
    ): ResultActions {
        val codeJson = code?.let { ""","code":"$it"""" }.orEmpty()
        return post("/internal/entries", """{"expoId":"$EXPO","participationType":"STANDARD","participantId":$participantId$codeJson}""")
    }

    private fun smsTry(): ResultActions =
        post(
            "/internal/participants/sms-try",
            """{"expoId":"$EXPO","participationType":"STANDARD","phoneNumber":"$PHONE","eventId":"${UUID.randomUUID()}"}""",
        )

    private fun codes() = jdbcTemplate.queryForList("SELECT code FROM tb_standard_participant ORDER BY id", String::class.java)

    /** 등록 완료 이벤트마다 문자에 담은 참가자 `[{id, code}]` 배열을 등록 순서대로 돌려준다. */
    private fun registeredParticipants() =
        jdbcTemplate
            .queryForList(
                "SELECT participants_json FROM tb_registration_outbox WHERE event_type = 'REGISTERED' ORDER BY id",
                String::class.java,
            ).map { mapper.readTree(it).toList() }

    private fun count(from: String) = jdbcTemplate.queryForObject("SELECT count(*) FROM $from", Long::class.java)

    private fun post(
        path: String,
        body: String,
    ): ResultActions =
        mockMvc.perform(post(path).header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON).content(body))

    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-0000000000c2"
        const val PHONE = "01012345678"
        val ALICE = Person("앨리스", "TEACHER", "광주고")
        val BOB = Person("밥", "GENERAL")
        val CAROL = Person("캐롤", "GENERAL")
        val DAVE = Person("데이브", "GENERAL")
        val EVE = Person("이브", "GENERAL")
        val FRANK = Person("프랭크", "GENERAL")
    }
}
