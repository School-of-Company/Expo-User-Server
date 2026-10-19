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
        count("tb_registration_outbox WHERE event_type = 'STANDARD_CREATED'") shouldBe 3
    }

    @Test
    fun `같은 번호로 같은 동행자를 다시 신청하면 사람을 만들지 않고 문자 이벤트도 만들지 않는다`() {
        register(ALICE, BOB).andExpect(status().isCreated)
        val codes = codes()

        register(ALICE, BOB).andExpect(status().isOk)

        count("tb_standard_participant") shouldBe 2
        codes() shouldBe codes
        registeredParticipants().size shouldBe 1
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
    }

    @Test
    fun `처음 신청의 일행 전체는 대표자가 먼저이고 문자 대상과 같다`() {
        val response =
            register(ALICE, BOB, CAROL)
                .andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString

        val ids = allIds()
        ids.size shouldBe 3
        longs(response, "groupParticipantIds") shouldBe ids
        longs(response, "participantIds") shouldBe ids
    }

    @Test
    fun `동행자를 추가하는 재신청은 문자 대상이 새 동행자뿐이어도 일행 전체를 돌려준다`() {
        register(ALICE, BOB).andExpect(status().isCreated)

        val response =
            register(ALICE, BOB, CAROL)
                .andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString

        val ids = allIds()
        ids.size shouldBe 3
        // 문자 대상은 새로 만든 동행자 한 명이고, 일행 전체는 대표자와 기존·신규 동행자 전원이다
        longs(response, "participantIds") shouldBe listOf(ids.last())
        longs(response, "groupParticipantIds") shouldBe ids
    }

    @Test
    fun `새 동행자가 없는 재신청은 기존 전원이 문자 대상이고 일행 전체와 같다`() {
        register(ALICE, BOB).andExpect(status().isCreated)

        val response =
            register(ALICE, BOB)
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        val ids = allIds()
        longs(response, "participantIds") shouldBe ids
        longs(response, "groupParticipantIds") shouldBe ids
    }

    @Test
    fun `취소된 동행자 중 일부만 다시 신청하면 다시 신청하지 않은 동행자는 일행에서 빠진다`() {
        register(ALICE, BOB, CAROL).andExpect(status().isCreated)
        val (alice, bob, carol) = allIds()
        val events = count("tb_registration_outbox WHERE event_type = 'REGISTERED'")

        val body = bodyOf(register(ALICE, BOB, released = listOf(bob, carol)), 200)

        longs(body, "groupParticipantIds") shouldBe listOf(alice, bob)
        // 새 동행자가 없으므로 문자 대상 목록도 이번 일행이고, 일행의 부분집합이다
        longs(body, "participantIds") shouldBe listOf(alice, bob)
        count("tb_standard_participant") shouldBe 3
        count("tb_registration_outbox WHERE event_type = 'REGISTERED'") shouldBe events
    }

    @Test
    fun `취소된 동행자 중 일부를 다시 신청하며 새 동행자를 더하면 새 동행자만 문자 대상이다`() {
        register(ALICE, BOB, CAROL).andExpect(status().isCreated)
        val (alice, bob, carol) = allIds()

        val body = bodyOf(register(ALICE, BOB, DAVE, released = listOf(bob, carol)), 201)

        val dave = idsOf(DAVE).single()
        longs(body, "groupParticipantIds") shouldBe listOf(alice, bob, dave)
        longs(body, "participantIds") shouldBe listOf(dave)
    }

    @Test
    fun `취소된 동행자를 아무도 다시 신청하지 않으면 일행은 대표자뿐이다`() {
        register(ALICE, BOB, CAROL).andExpect(status().isCreated)
        val (alice, bob, carol) = allIds()

        val body = bodyOf(register(ALICE, released = listOf(bob, carol)), 200)

        longs(body, "groupParticipantIds") shouldBe listOf(alice)
        longs(body, "participantIds") shouldBe listOf(alice)
    }

    @Test
    fun `취소된 동행자를 전부 다시 신청하거나 취소 목록이 비면 일행은 기존 전원이다`() {
        register(ALICE, BOB, CAROL).andExpect(status().isCreated)
        val ids = allIds()

        listOf(ids.drop(1), emptyList()).forEach { released ->
            val body = bodyOf(register(ALICE, BOB, CAROL, released = released), 200)

            longs(body, "groupParticipantIds") shouldBe ids
            longs(body, "participantIds") shouldBe ids
        }
    }

    @Test
    fun `일행 전체를 취소하고 새 동행자로 재신청해도 managed 목록은 5명을 넘지 않는다`() {
        register(ALICE, BOB, CAROL, DAVE, EVE).andExpect(status().isCreated)
        val (alice, bob, carol, dave, eve) = allIds()

        val body = bodyOf(register(ALICE, FRANK, released = listOf(bob, carol, dave, eve)), 201)

        val frank = idsOf(FRANK).single()
        longs(body, "groupParticipantIds") shouldBe listOf(alice, frank)
        // 행과 code는 남는다
        count("tb_standard_participant") shouldBe 6
        managedIds() shouldBe listOf(alice, frank)
    }

    @Test
    fun `취소됐던 동행자가 다시 신청하면 managed 목록에 돌아오고 취소 목록이 없는 재신청은 표시를 바꾸지 않는다`() {
        register(ALICE, BOB, CAROL).andExpect(status().isCreated)
        val (alice, bob, carol) = allIds()

        register(ALICE, BOB, released = listOf(bob, carol)).andExpect(status().isOk)
        managedIds() shouldBe listOf(alice, bob)

        register(ALICE, BOB).andExpect(status().isOk)
        managedIds() shouldBe listOf(alice, bob)

        register(ALICE, BOB, CAROL).andExpect(status().isOk)
        managedIds() shouldBe listOf(alice, bob, carol)
    }

    @Test
    fun `같은 requestId의 재시도는 그 사이 다른 신청이 동행자를 더해도 처음의 일행 전체를 돌려준다`() {
        val requestId = UUID.randomUUID().toString()
        val first =
            register(ALICE, BOB, requestId = requestId)
                .andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString
        val before = longs(first, "groupParticipantIds")
        before.size shouldBe 2

        // 응답을 잃은 사이 다른 신청(요청 키 없음)이 같은 대표자에게 동행자를 더한다
        register(ALICE, BOB, CAROL).andExpect(status().isCreated)
        allIds().size shouldBe 3

        val retry =
            register(ALICE, BOB, requestId = requestId)
                .andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString

        longs(retry, "groupParticipantIds") shouldBe before
        longs(retry, "participantIds") shouldBe longs(first, "participantIds")
    }

    @Test
    fun `일행 전체 목록에도 code를 싣지 않는다`() {
        val response =
            register(ALICE, BOB)
                .andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString

        val codes = jdbcTemplate.queryForList("SELECT code FROM tb_standard_participant", String::class.java)
        codes.none { response.contains(it.orEmpty()) } shouldBe true
        response.contains("\"code\"") shouldBe false
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
    fun `문자를 두 번 보낸 대표자도 새 동행자는 누적 5명까지 더할 수 있다`() {
        register(ALICE).andExpect(status().isCreated)
        repeat(2) { smsTry() }

        // 동행자를 한 명씩 나눠 더해도 문자 횟수(smsTryTime)와 무관하게 계속 더해진다.
        register(ALICE, BOB).andExpect(status().isCreated)
        smsTry()
        register(ALICE, BOB, CAROL).andExpect(status().isCreated)

        count("tb_standard_participant") shouldBe 3
    }

    @Test
    fun `문자를 두 번 보낸 대표자의 새 동행자도 누적 5명은 넘을 수 없다`() {
        register(ALICE, BOB, CAROL, DAVE).andExpect(status().isCreated)
        repeat(2) { smsTry() }

        register(ALICE, EVE, FRANK).andExpect(status().isConflict)

        count("tb_standard_participant") shouldBe 4
    }

    @Test
    fun `새 동행자 없는 재신청은 발송 횟수가 0 1 2 어느 쪽이든 200이고 기존 QR 그대로 문자 이벤트를 만들지 않는다`() {
        register(ALICE, BOB).andExpect(status().isCreated)
        val ids = allIds()
        val codes = codes()
        val events = count("tb_registration_outbox WHERE event_type = 'REGISTERED'")
        val createdEvents = count("tb_registration_outbox WHERE event_type = 'STANDARD_CREATED'")

        repeat(3) { tries ->
            val body = bodyOf(register(ALICE, BOB), 200)

            // 응답 모양은 그대로다: 문자 대상 목록은 대표자와 기존 일행이고 일행 전체도 같다
            longs(body, "participantIds") shouldBe ids
            longs(body, "groupParticipantIds") shouldBe ids
            body.contains("\"code\"") shouldBe false
            count("tb_standard_participant") shouldBe 2
            codes() shouldBe codes
            count("tb_registration_outbox WHERE event_type = 'REGISTERED'") shouldBe events
            count("tb_registration_outbox WHERE event_type = 'STANDARD_CREATED'") shouldBe createdEvents
            if (tries < 2) smsTry()
        }
    }

    @Test
    fun `문자를 두 번 보낸 뒤에도 새 동행자가 있으면 그 동행자만 문자 대상이다`() {
        register(ALICE, BOB).andExpect(status().isCreated)
        repeat(2) { smsTry() }

        val body = bodyOf(register(ALICE, BOB, CAROL), 201)

        val carol = idsOf(CAROL)
        longs(body, "participantIds") shouldBe carol
        longs(body, "groupParticipantIds") shouldBe allIds()
        val events = registeredParticipants()
        events.size shouldBe 2
        events.last().map { it.path("id").asLong() } shouldBe carol
    }

    @Test
    fun `문자를 두 번 보낸 대표자의 같은 requestId 재시도는 처음 응답을 돌려주고 이벤트를 만들지 않는다`() {
        register(ALICE, BOB).andExpect(status().isCreated)
        repeat(2) { smsTry() }
        val requestId = UUID.randomUUID().toString()
        val first = bodyOf(register(ALICE, BOB, requestId = requestId), 200)
        val events = count("tb_registration_outbox WHERE event_type = 'REGISTERED'")

        val retry = bodyOf(register(ALICE, BOB, requestId = requestId), 200)

        retry shouldBe first
        count("tb_registration_outbox WHERE event_type = 'REGISTERED'") shouldBe events
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
        phone: String = PHONE,
        released: List<Long>? = null,
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
                "phoneNumber" to phone,
                "informationJson" to mapper.writeValueAsString(answers),
                "personalInformationStatus" to true,
                "applicationType" to "PRE",
                "occupation" to representative.occupation,
                "questions" to listOf(mapOf("id" to "1", "title" to "동행자", "formType" to "COMPANION")),
                requestId?.let { "requestId" to it },
                released?.let { "releasedParticipantIds" to it },
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

    private fun idsOf(vararg people: Person): List<Long> =
        people.map {
            jdbcTemplate.queryForObject("SELECT id FROM tb_standard_participant WHERE name = ?", Long::class.java, it.name)!!
        }

    private fun codes() = jdbcTemplate.queryForList("SELECT code FROM tb_standard_participant ORDER BY id", String::class.java)

    /** 등록 완료 이벤트마다 문자에 담은 참가자 `[{id, code}]` 배열을 등록 순서대로 돌려준다. */
    private fun registeredParticipants() =
        jdbcTemplate
            .queryForList(
                "SELECT participants_json FROM tb_registration_outbox WHERE event_type = 'REGISTERED' ORDER BY id",
                String::class.java,
            ).map { mapper.readTree(it).toList() }

    /** 저장된 참가자 `id`를 대표자가 먼저, 이후 `id` 순서로 돌려준다(응답의 일행 전체 순서와 같다). */
    private fun allIds(): List<Long> =
        jdbcTemplate.queryForList("SELECT id FROM tb_standard_participant ORDER BY id", Long::class.java).map { it!! }

    private fun bodyOf(
        result: ResultActions,
        expectedStatus: Int,
    ): String {
        val response = result.andReturn().response
        response.status shouldBe expectedStatus
        return response.contentAsString
    }

    private fun longs(
        json: String,
        field: String,
    ): List<Long> =
        mapper
            .readTree(json)
            .path(field)
            .toList()
            .map { it.asLong() }

    private fun managedIds(): List<Long> =
        mapper
            .readTree(
                bodyOf(post("/internal/standard-participants/managed", """{"expoId":"$EXPO","phoneNumber":"$PHONE"}"""), 200),
            ).path("participants")
            .toList()
            .map { it.path("participantId").asLong() }

    private fun count(from: String) = jdbcTemplate.queryForObject("SELECT count(*) FROM $from", Long::class.java)

    private fun post(
        path: String,
        body: String,
    ): ResultActions =
        mockMvc.perform(post(path).header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON).content(body))

    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-0000000000c2"
        const val PHONE = "01012345678"
        const val OTHER_PHONE = "01099998888"
        val ALICE = Person("앨리스", "TEACHER", "광주고")
        val BOB = Person("밥", "GENERAL")
        val CAROL = Person("캐롤", "GENERAL")
        val DAVE = Person("데이브", "GENERAL")
        val EVE = Person("이브", "GENERAL")
        val FRANK = Person("프랭크", "GENERAL")
    }
}
