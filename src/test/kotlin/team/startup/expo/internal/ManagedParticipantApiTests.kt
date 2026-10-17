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
import team.startup.expo.domain.participation.entity.Occupation
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.support.IntegrationTestSupport
import java.time.LocalDateTime

class ManagedParticipantApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var standardParticipantRepository: StandardParticipantRepository

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_expo_deletion, tb_standard_participant RESTART IDENTITY CASCADE")
    }

    // --- POST /internal/standard-participants/managed

    @Test
    fun `대표자 본인과 동행자를 id 순서로 돌려주고 대표자 표시와 저장된 직업을 싣는다`() {
        val representative = save("대표", PHONE, Occupation.TEACHER)
        val first = save("동행1", null, Occupation.ELEMENTARY_STUDENT, representative)
        val second = save("동행2", null, Occupation.SCHOOL_STAFF, representative)

        managed(EXPO, PHONE)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.registered").value(true))
            .andExpect(jsonPath("$.participants.length()").value(3))
            .andExpect(jsonPath("$.participants[0].participantId").value(representative))
            .andExpect(jsonPath("$.participants[0].representative").value(true))
            .andExpect(jsonPath("$.participants[0].occupation").value("TEACHER"))
            .andExpect(jsonPath("$.participants[0].applicationType").value("PRE"))
            .andExpect(jsonPath("$.participants[1].participantId").value(first))
            .andExpect(jsonPath("$.participants[1].representative").value(false))
            .andExpect(jsonPath("$.participants[1].occupation").value("ELEMENTARY_STUDENT"))
            .andExpect(jsonPath("$.participants[2].participantId").value(second))
            .andExpect(jsonPath("$.participants[2].occupation").value("SCHOOL_STAFF"))
    }

    @Test
    fun `응답에 전화번호와 QR code를 싣지 않는다`() {
        val representative = save("대표", PHONE, Occupation.TEACHER)
        save("동행1", null, Occupation.GENERAL, representative)
        val code =
            jdbcTemplate.queryForObject(
                "SELECT code FROM tb_standard_participant WHERE id = ?",
                String::class.java,
                representative,
            )!!

        val body =
            managed(EXPO, PHONE)
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        check(!body.contains(PHONE)) { "응답에 전화번호가 있으면 안 된다" }
        check(!body.contains(code)) { "응답에 code가 있으면 안 된다" }
        check(!body.contains("phoneNumber") && !body.contains("code")) { "응답에 전화번호나 code 필드가 있으면 안 된다" }
    }

    @Test
    fun `이 번호로 등록된 대표자가 없으면 오류가 아니라 registered false이다`() {
        managed(EXPO, PHONE)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.registered").value(false))
            .andExpect(jsonPath("$.participants.length()").value(0))
    }

    @Test
    fun `다른 박람회에 등록된 같은 번호는 등록으로 보지 않는다`() {
        save("대표", PHONE, Occupation.TEACHER, expoId = OTHER_EXPO)

        managed(EXPO, PHONE).andExpect(status().isOk).andExpect(jsonPath("$.registered").value(false))
    }

    @Test
    fun `다른 대표자의 동행자는 목록에 섞이지 않는다`() {
        val mine = save("대표", PHONE, Occupation.TEACHER)
        val other = save("남대표", OTHER_PHONE, Occupation.TEACHER)
        save("내동행", null, Occupation.GENERAL, mine)
        save("남동행", null, Occupation.GENERAL, other)

        managed(EXPO, PHONE)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participants.length()").value(2))
            .andExpect(jsonPath("$.participants[1].name").value("내동행"))
    }

    @Test
    fun `표기가 달라도 같은 번호의 대표자로 찾고 직업이 없는 옛 행은 null이다`() {
        save("대표", "010-1234-5678", null)

        managed(EXPO, "01012345678")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.registered").value(true))
            .andExpect(jsonPath("$.participants[0].occupation").doesNotExist())
    }

    @Test
    fun `표기만 다른 같은 번호의 대표자가 여럿이면 하나로 특정할 수 없어 409이다`() {
        save("대표1", "010-1234-5678", Occupation.TEACHER)
        save("대표2", "0101234-5678", Occupation.TEACHER)

        managed(EXPO, "01012345678").andExpect(status().isConflict)
    }

    @Test
    fun `잘못된 요청은 400이다`() {
        post("/internal/standard-participants/managed", """{"expoId":"$EXPO","phoneNumber":""}""").andExpect(status().isBadRequest)
        post("/internal/standard-participants/managed", """{"phoneNumber":"$PHONE"}""").andExpect(status().isBadRequest)
        managed(EXPO, "abc").andExpect(status().isBadRequest)
    }

    // --- POST /internal/standard-participants/managed/select

    @Test
    fun `선택한 참가자가 모두 내 관리 대상이면 요청 순서대로 저장된 값을 돌려준다`() {
        val representative = save("대표", PHONE, Occupation.TEACHER, applicationType = ApplicationType.FIELD)
        val companion = save("동행", null, Occupation.HIGH_SCHOOL_STUDENT, representative)

        select(EXPO, PHONE, listOf(companion, representative, companion))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].participantId").value(companion))
            .andExpect(jsonPath("$[0].occupation").value("HIGH_SCHOOL_STUDENT"))
            .andExpect(jsonPath("$[0].representative").value(false))
            .andExpect(jsonPath("$[0].applicationType").value("PRE"))
            .andExpect(jsonPath("$[1].participantId").value(representative))
            .andExpect(jsonPath("$[1].representative").value(true))
            .andExpect(jsonPath("$[1].applicationType").value("FIELD"))
    }

    @Test
    fun `다른 대표자의 참가자나 다른 박람회의 참가자가 섞이면 일부만 돌려주지 않고 404이다`() {
        val mine = save("대표", PHONE, Occupation.TEACHER)
        val other = save("남대표", OTHER_PHONE, Occupation.TEACHER)
        val otherCompanion = save("남동행", null, Occupation.GENERAL, other)
        val otherExpo = save("다른행사", "01099990000", Occupation.GENERAL, expoId = OTHER_EXPO)

        select(EXPO, PHONE, listOf(mine, other)).andExpect(status().isNotFound)
        select(EXPO, PHONE, listOf(mine, otherCompanion)).andExpect(status().isNotFound)
        select(EXPO, PHONE, listOf(mine, otherExpo)).andExpect(status().isNotFound)
        select(EXPO, PHONE, listOf(mine, otherExpo + 1000)).andExpect(status().isNotFound)
    }

    @Test
    fun `등록되지 않은 번호는 남의 참가자를 선택할 수 없고 존재 여부도 구분되지 않는다`() {
        val other = save("남대표", OTHER_PHONE, Occupation.TEACHER)

        select(EXPO, PHONE, listOf(other)).andExpect(status().isNotFound)
        select(EXPO, PHONE, listOf(other + 1000)).andExpect(status().isNotFound)
    }

    @Test
    fun `선택 목록이 비었거나 5명을 넘으면 400이다`() {
        select(EXPO, PHONE, emptyList()).andExpect(status().isBadRequest)
        select(EXPO, PHONE, (1L..6L).toList()).andExpect(status().isBadRequest)
    }

    @Test
    fun `선택 응답에도 전화번호와 QR code를 싣지 않는다`() {
        val representative = save("대표", PHONE, Occupation.TEACHER)

        val body =
            select(EXPO, PHONE, listOf(representative))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        check(!body.contains(PHONE) && !body.contains("phoneNumber") && !body.contains("code")) { "응답에 전화번호나 code가 있으면 안 된다" }
    }

    // --- 인증

    @Test
    fun `토큰이 없거나 틀리거나 관리자 토큰만 있으면 두 경로 모두 401이다`() {
        val requests =
            mapOf(
                "/internal/standard-participants/managed" to """{"expoId":"$EXPO","phoneNumber":"$PHONE"}""",
                "/internal/standard-participants/managed/select" to """{"expoId":"$EXPO","phoneNumber":"$PHONE","participantIds":[1]}""",
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
    }

    private fun managed(
        expoId: String,
        phone: String,
    ): ResultActions = post("/internal/standard-participants/managed", """{"expoId":"$expoId","phoneNumber":"$phone"}""")

    private fun select(
        expoId: String,
        phone: String,
        ids: List<Long>,
    ): ResultActions =
        post(
            "/internal/standard-participants/managed/select",
            """{"expoId":"$expoId","phoneNumber":"$phone","participantIds":[${ids.joinToString(",")}]}""",
        )

    private fun post(
        path: String,
        body: String,
    ): ResultActions =
        mockMvc.perform(post(path).header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun save(
        name: String,
        phone: String?,
        occupation: Occupation?,
        representativeId: Long? = null,
        expoId: String = EXPO,
        applicationType: ApplicationType = ApplicationType.PRE,
    ): Long =
        standardParticipantRepository
            .save(
                StandardParticipant(
                    expoId = expoId,
                    name = name,
                    phoneNumber = phone,
                    personalInformationStatus = true,
                    applicationType = applicationType,
                    applicationDate = LocalDateTime.of(2026, 10, 1, 10, 0),
                    occupation = occupation,
                    representativeId = representativeId,
                ),
            ).id!!

    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-0000000000f1"
        const val OTHER_EXPO = "0199aaaa-0000-7000-8000-0000000000f2"
        const val PHONE = "01012345678"
        const val OTHER_PHONE = "01055556666"
    }
}
