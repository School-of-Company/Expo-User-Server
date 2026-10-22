package team.startup.expo.internal

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.support.IntegrationTestSupport
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.util.UUID

class FieldVisitApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var participants: StandardParticipantRepository

    @Autowired
    private lateinit var json: JsonMapper

    @BeforeEach
    fun clear() {
        jdbcTemplate.execute(
            "TRUNCATE tb_standard_participant, tb_field_visit_creation, tb_field_visit_operation, " +
                "tb_registration_outbox RESTART IDENTITY CASCADE",
        )
    }

    @Test
    fun `신규 매핑을 재현하고 pending 다른키 우회와 변경된 payload를 거절한다`() {
        val first =
            create("request-1")
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
        create("request-1")
            .andExpect(status().isOk)
            .andReturn()
            .response.contentAsString shouldBe first
        create("request-1", "변경").andExpect(status().isConflict)
        create("request-2").andExpect(status().isConflict)
        val mapping = json.readTree(first)
        mapping[0].path("clientReference").asString() shouldBe "new-1"
        mapping[1].path("clientReference").asString() shouldBe "new-2"
        mapping[0].path("participantId").asLong() shouldBe 1L
        mapping[1].path("participantId").asLong() shouldBe 2L
        count("tb_standard_participant") shouldBe 2L
        count("tb_field_visit_creation") shouldBe 1L
        count("tb_registration_outbox WHERE event_type = 'REGISTERED'") shouldBe 1L
    }

    @Test
    fun `PRE 참가자 방문일 projection과 입장은 프로필 QR을 보존하고 날짜별 조회한다`() {
        val person =
            participants.saveAndFlush(
                StandardParticipant(
                    expoId = EXPO,
                    name = "기존",
                    phoneNumber = PHONE,
                    personalInformationStatus = true,
                    applicationType = ApplicationType.PRE,
                    applicationDate = LocalDateTime.of(2026, 1, 1, 12, 0),
                ),
            )
        val id = person.id!!
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        project(id, first, "2026-10-21", 1, "ACTIVE").andExpect(status().isNoContent)
        project(id, second, "2026-10-22", 1, "ACTIVE").andExpect(status().isNoContent)
        enter(id, person.code, first, "2026-10-21").andExpect(status().isOk)
        enter(id, person.code, first, "2026-10-21").andExpect(status().isConflict)
        enter(id, person.code, second, "2026-10-22").andExpect(status().isOk)
        entryStatus(
            id,
            "2026-10-21",
        ).andExpect(jsonPath("$.entered").value(true)).andExpect(jsonPath("$.attendanceDate").value("2026-10-21"))
        entryStatus(id, "2026-10-23").andExpect(jsonPath("$.entered").value(false))
        val after = participants.findById(id).orElseThrow()
        after.applicationType shouldBe ApplicationType.PRE
        after.name shouldBe person.name
        after.code shouldBe person.code
        after.applicationDate shouldBe person.applicationDate
        count("tb_registration_outbox") shouldBe 0L
    }

    @Test
    fun `같은 세대 취소는 terminal이고 지연된 이전 세대는 새 ACTIVE를 되돌리지 않는다`() {
        create("request-1").andExpect(status().isOk)
        val id = 1L
        val registration = UUID.randomUUID()
        project(id, registration, "2026-10-22", 1, "CANCELLED").andExpect(status().isNoContent)
        project(id, registration, "2026-10-22", 1, "ACTIVE").andExpect(status().isNoContent)
        jdbcTemplate.queryForObject("SELECT status FROM tb_field_visit", String::class.java) shouldBe "CANCELLED"
        project(id, registration, "2026-10-22", 2, "ACTIVE").andExpect(status().isNoContent)
        project(id, registration, "2026-10-22", 1, "CANCELLED").andExpect(status().isNoContent)
        jdbcTemplate.queryForObject("SELECT generation FROM tb_field_visit", Long::class.java) shouldBe 2L
        jdbcTemplate.queryForObject("SELECT status FROM tb_field_visit", String::class.java) shouldBe "ACTIVE"
    }

    @Test
    fun `새 엔드포인트는 사용자 헤더나 access token 대신 내부 토큰을 요구한다`() {
        mvc
            .perform(
                post("/internal/expos/$EXPO/field-visit-participants")
                    .header("X-User-Id", "1")
                    .header("X-User-Role", "ADMIN")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody("key", "새사람")),
            ).andExpect(status().isUnauthorized)
        count("tb_standard_participant") shouldBe 0L
    }

    @Test
    fun `방문건수 대신 활성 사람을 세고 마지막 방문 취소 뒤에도 소유권은 보존한다`() {
        val body =
            mapOf(
                "phoneNumber" to PHONE,
                "requestId" to "five",
                "personalInformationStatus" to true,
                "participants" to (1..5).map { mapOf("clientReference" to "new-$it", "name" to "참가$it") },
            )
        mvc
            .perform(
                post("/internal/expos/$EXPO/field-visit-participants")
                    .header("X-Internal-Token", INTERNAL_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)),
            ).andExpect(status().isOk)
        val registrations = (1L..5L).associateWith { UUID.randomUUID() }
        registrations.forEach { (id, registration) -> project(id, registration, "2026-10-22", 1, "ACTIVE").andExpect(status().isNoContent) }
        val otherDay = UUID.randomUUID()
        project(2, otherDay, "2026-10-23", 1, "ACTIVE").andExpect(status().isNoContent)
        project(2, registrations.getValue(2), "2026-10-22", 1, "CANCELLED").andExpect(status().isNoContent)
        mvc
            .perform(
                post("/internal/expos/$EXPO/field-visit-participants")
                    .header("X-Internal-Token", INTERNAL_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            body + ("requestId" to "six") +
                                ("participants" to listOf(mapOf("clientReference" to "new-six", "name" to "여섯째"))),
                        ),
                    ),
            ).andExpect(status().isConflict)
        project(2, otherDay, "2026-10-23", 1, "CANCELLED").andExpect(status().isNoContent)
        mvc
            .perform(
                post("/internal/expos/$EXPO/field-visit-participants")
                    .header("X-Internal-Token", INTERNAL_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            body + ("requestId" to "six") +
                                ("participants" to listOf(mapOf("clientReference" to "new-six", "name" to "여섯째"))),
                        ),
                    ),
            ).andExpect(status().isOk)
        project(2, registrations.getValue(2), "2026-10-22", 2, "ACTIVE").andExpect(status().isConflict)
        mvc
            .perform(
                post("/internal/standard-participants/managed")
                    .header("X-Internal-Token", INTERNAL_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(mapOf("expoId" to EXPO, "phoneNumber" to PHONE))),
            ).andExpect(
                status().isOk,
            ).andExpect(jsonPath("$.registered").value(true))
            .andExpect(jsonPath("$.participants.length()").value(6))
    }

    private fun create(
        key: String,
        name: String = "새사람",
    ) = mvc.perform(
        post("/internal/expos/$EXPO/field-visit-participants")
            .header("X-Internal-Token", INTERNAL_TOKEN)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody(key, name)),
    )

    private fun createBody(
        key: String,
        name: String,
    ) = json.writeValueAsString(
        mapOf(
            "phoneNumber" to PHONE,
            "requestId" to key,
            "personalInformationStatus" to true,
            "participants" to
                listOf(mapOf("clientReference" to "new-1", "name" to name), mapOf("clientReference" to "new-2", "name" to "동행")),
        ),
    )

    private fun project(
        id: Long,
        registration: UUID,
        date: String,
        generation: Long,
        state: String,
    ) = mvc.perform(
        put("/internal/expos/$EXPO/participants/$id/field-visits/$registration")
            .header("X-Internal-Token", INTERNAL_TOKEN)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(
                    mapOf(
                        "visitDate" to date,
                        "generation" to generation,
                        "status" to state,
                        "operationId" to UUID.randomUUID().toString(),
                    ),
                ),
            ),
    )

    private fun enter(
        id: Long,
        code: String,
        registration: UUID,
        date: String,
    ) = mvc.perform(
        post("/internal/entries")
            .header("X-Internal-Token", INTERNAL_TOKEN)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(
                    mapOf(
                        "expoId" to EXPO,
                        "participationType" to "STANDARD",
                        "participantId" to id,
                        "code" to code,
                        "attendanceDate" to date,
                        "fieldVisitRegistrationId" to registration.toString(),
                        "fieldVisitGeneration" to 1,
                    ),
                ),
            ),
    )

    private fun entryStatus(
        id: Long,
        date: String,
    ) = mvc.perform(
        post(
            "/internal/standard-participants/entry-status",
        ).header("X-Internal-Token", INTERNAL_TOKEN)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(mapOf("expoId" to EXPO, "participantId" to id, "attendanceDate" to date))),
    )

    private fun count(table: String) = jdbcTemplate.queryForObject("SELECT count(*) FROM $table", Long::class.java)!!

    companion object {
        const val EXPO = "expo-202"
        const val PHONE = "01012345678"
    }
}
