package team.startup.expo.internal

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.support.IntegrationTestSupport
import java.time.LocalDateTime

class ParticipantStatsApiTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var standardParticipantRepository: StandardParticipantRepository

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE tb_expo_deletion, tb_participant_phone_verification, tb_standard_participant RESTART IDENTITY CASCADE",
        )
    }

    @Test
    fun `박람회의 인증한 고유 번호 수와 등록한 고유 참가자 수를 돌려준다`() {
        val representative = participant("대표", "01011110001", null, EXPO)
        participant("동행", null, representative, EXPO)
        participant("다른행사", "01022220002", null, OTHER_EXPO)
        verified(EXPO, "hash-1")
        verified(EXPO, "hash-2")
        verified(OTHER_EXPO, "hash-3")

        stats(EXPO)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.expoId").value(EXPO))
            .andExpect(jsonPath("$.verifiedPhoneCount").value(2))
            // 동행자도 각각 한 명이다
            .andExpect(jsonPath("$.registeredParticipantCount").value(2))
        stats(OTHER_EXPO)
            .andExpect(jsonPath("$.verifiedPhoneCount").value(1))
            .andExpect(jsonPath("$.registeredParticipantCount").value(1))
    }

    @Test
    fun `기록이 없거나 없는 박람회는 오류가 아니라 0건이다`() {
        stats(EXPO)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.verifiedPhoneCount").value(0))
            .andExpect(jsonPath("$.registeredParticipantCount").value(0))
    }

    @Test
    fun `응답에는 전화번호나 해시가 없다`() {
        participant("대표", "01011110001", null, EXPO)
        verified(EXPO, "secret-hash-value")

        val body = stats(EXPO).andReturn().response.contentAsString

        check(!body.contains("01011110001") && !body.contains("secret-hash-value")) { "집계 응답에 번호나 해시가 있으면 안 된다" }
        Regex("\"(\\w+)\":").findAll(body).map { it.groupValues[1] }.toSet() shouldBeSet
            setOf("expoId", "verifiedPhoneCount", "registeredParticipantCount")
    }

    @Test
    fun `토큰이 없거나 틀리거나 관리자 토큰만 있으면 401이다`() {
        val path = "/internal/expos/$EXPO/participant-stats"

        mockMvc.perform(get(path)).andExpect(status().isUnauthorized)
        mockMvc.perform(get(path).header("X-Internal-Token", "wrong")).andExpect(status().isUnauthorized)
        mockMvc.perform(get(path).header("Authorization", bearerOf(1L))).andExpect(status().isUnauthorized)
    }

    private infix fun Set<String>.shouldBeSet(expected: Set<String>) {
        check(this == expected) { "응답 필드가 $this 이고 기대한 값은 $expected 이다" }
    }

    private fun stats(expoId: String): ResultActions =
        mockMvc.perform(
            get("/internal/expos/$expoId/participant-stats")
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .accept(MediaType.APPLICATION_JSON),
        )

    private fun verified(
        expoId: String,
        hash: String,
    ) {
        jdbcTemplate.update(
            "INSERT INTO tb_participant_phone_verification (expo_id, phone_hash, first_verified_at) VALUES (?, ?, now())",
            expoId,
            hash.padEnd(64, '0'),
        )
    }

    private fun participant(
        name: String,
        phone: String?,
        representativeId: Long?,
        expoId: String,
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
                    representativeId = representativeId,
                ),
            ).id!!

    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-000000000f11"
        const val OTHER_EXPO = "0199aaaa-0000-7000-8000-000000000f12"
    }
}
