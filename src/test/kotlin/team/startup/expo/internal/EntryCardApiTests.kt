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
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.domain.training.entity.Trainee
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.support.IntegrationTestSupport
import java.time.LocalDateTime

class EntryCardApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var standardParticipantRepository: StandardParticipantRepository

    @Autowired
    private lateinit var traineeRepository: TraineeRepository

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_trainee, tb_standard_participant RESTART IDENTITY CASCADE")
    }

    @Test
    fun `참가자와 연수자 카드를 요청 순서대로 주고 중복 id는 한 번만 준다`() {
        val teacher = standard(EXPO, "01000000001", "교사", occupation = "TEACHER", school = "빛고을고")
        val companion = standard(EXPO, null, "동행", agree = false)
        val trainee = trainee(EXPO, "01011110001", "연수", school = "빛고을초")

        entryCards(EXPO, listOf(companion, teacher, companion), listOf(trainee))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.standardParticipants.length()").value(2))
            .andExpect(jsonPath("$.standardParticipants[0].participantId").value(companion))
            .andExpect(jsonPath("$.standardParticipants[0].phoneNumber").doesNotExist())
            .andExpect(jsonPath("$.standardParticipants[0].personalInformationStatus").value(false))
            .andExpect(jsonPath("$.standardParticipants[0].occupation").doesNotExist())
            .andExpect(jsonPath("$.standardParticipants[1].participantId").value(teacher))
            .andExpect(jsonPath("$.standardParticipants[1].name").value("교사"))
            .andExpect(jsonPath("$.standardParticipants[1].phoneNumber").value("01000000001"))
            .andExpect(jsonPath("$.standardParticipants[1].occupation").value("TEACHER"))
            .andExpect(jsonPath("$.standardParticipants[1].school").value("빛고을고"))
            .andExpect(jsonPath("$.standardParticipants[1].information").doesNotExist())
            .andExpect(jsonPath("$.trainees.length()").value(1))
            .andExpect(jsonPath("$.trainees[0].traineeId").value(trainee))
            .andExpect(jsonPath("$.trainees[0].name").value("연수"))
            .andExpect(jsonPath("$.trainees[0].phoneNumber").value("01011110001"))
            .andExpect(jsonPath("$.trainees[0].personalInformationStatus").value(true))
            .andExpect(jsonPath("$.trainees[0].school").value("빛고을초"))
            .andExpect(jsonPath("$.trainees[0].occupation").doesNotExist())
    }

    @Test
    fun `없는 id와 다른 박람회의 id는 404 없이 빠진다`() {
        val mine = standard(EXPO, "01000000001", "내참가자")
        val other = standard(OTHER_EXPO, "01000000002", "다른행사")
        val otherTrainee = trainee(OTHER_EXPO, "01011110001", "다른연수")

        entryCards(EXPO, listOf(other, mine, mine + 1000), listOf(otherTrainee, otherTrainee + 1000))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.standardParticipants.length()").value(1))
            .andExpect(jsonPath("$.standardParticipants[0].participantId").value(mine))
            .andExpect(jsonPath("$.trainees.length()").value(0))
    }

    @Test
    fun `두 목록이 비어도 빈 결과이고 목록이 상한을 넘거나 박람회 id가 없으면 400이다`() {
        entryCards(EXPO, emptyList(), emptyList())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.standardParticipants.length()").value(0))
            .andExpect(jsonPath("$.trainees.length()").value(0))
        entryCards(EXPO, (1L..1001L).toList(), emptyList()).andExpect(status().isBadRequest)
        entryCards(EXPO, emptyList(), (1L..1001L).toList()).andExpect(status().isBadRequest)
        entryCards("", listOf(1), emptyList()).andExpect(status().isBadRequest)
    }

    @Test
    fun `내부 토큰이 없으면 401이다`() {
        mockMvc
            .perform(
                post("/internal/participants/entry-cards")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"expoId":"$EXPO","standardParticipantIds":[1]}"""),
            ).andExpect(status().isUnauthorized)
    }

    private fun entryCards(
        expoId: String,
        standardIds: List<Long>,
        traineeIds: List<Long>,
    ): ResultActions =
        mockMvc.perform(
            post("/internal/participants/entry-cards")
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"expoId":"$expoId","standardParticipantIds":[${standardIds.joinToString(",")}],""" +
                        """"traineeIds":[${traineeIds.joinToString(",")}]}""",
                ),
        )

    private fun standard(
        expoId: String,
        phone: String?,
        name: String,
        agree: Boolean = true,
        occupation: String? = null,
        school: String? = null,
    ): Long {
        val id =
            standardParticipantRepository
                .save(
                    StandardParticipant(
                        expoId = expoId,
                        name = name,
                        phoneNumber = phone,
                        personalInformationStatus = agree,
                        applicationType = ApplicationType.PRE,
                        applicationDate = LocalDateTime.of(2026, 10, 1, 10, 0),
                    ),
                ).id!!
        jdbcTemplate.update("UPDATE tb_standard_participant SET occupation = ?, school = ? WHERE id = ?", occupation, school, id)
        return id
    }

    private fun trainee(
        expoId: String,
        phone: String,
        name: String,
        school: String? = null,
    ): Long {
        val id =
            traineeRepository
                .save(
                    Trainee(
                        expoId = expoId,
                        name = name,
                        phoneNumber = phone,
                        trainingId = null,
                        personalInformationStatus = true,
                        applicationType = ApplicationType.PRE,
                        applicationDate = LocalDateTime.of(2026, 10, 1, 10, 0),
                    ),
                ).id!!
        jdbcTemplate.update("UPDATE tb_trainee SET school = ? WHERE id = ?", school, id)
        return id
    }

    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-0000000000e1"
        const val OTHER_EXPO = "0199aaaa-0000-7000-8000-0000000000e2"
    }
}
