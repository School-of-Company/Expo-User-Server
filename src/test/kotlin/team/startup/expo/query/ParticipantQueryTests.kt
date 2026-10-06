package team.startup.expo.query

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.support.FakeExpoServer

class ParticipantQueryTests : QueryApiTestSupport() {
    @Test
    fun `date를 주지 않으면 오늘 입장한 참가자만 노션 명세의 형태로 돌려준다`() {
        val today = saveParticipant(EXPO_A, "홍길동", "01011112222", personalInformationStatus = true)
        val yesterday = saveParticipant(EXPO_A, "김영희", "01033334444")
        saveParticipant(EXPO_A, "미입장", "01055556666")
        attend(today, TODAY)
        attend(yesterday, TODAY.minusDays(1))

        fetch(EXPO_A)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.info.totalPage").value(1))
            .andExpect(jsonPath("$.info.totalElement").value(1))
            .andExpect(jsonPath("$.participants.length()").value(1))
            .andExpect(jsonPath("$.participants[0].id").value(today.id))
            .andExpect(jsonPath("$.participants[0].name").value("홍길동"))
            .andExpect(jsonPath("$.participants[0].phoneNumber").value("01011112222"))
            .andExpect(jsonPath("$.participants[0].informationStatus").value(true))
            // 목록 필드 이름은 v1과 Expo-Client가 쓰는 participants다(노션의 participant가 아니다)
            .andExpect(jsonPath("$.participant").doesNotExist())
    }

    @Test
    fun `date를 주면 그날 입장한 참가자를 조회한다`() {
        val today = saveParticipant(EXPO_A, "홍길동", "01011112222")
        val yesterday = saveParticipant(EXPO_A, "김영희", "01033334444", personalInformationStatus = false)
        attend(today, TODAY)
        attend(yesterday, TODAY.minusDays(1))

        fetch(EXPO_A, "date" to TODAY.minusDays(1).toString())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.info.totalElement").value(1))
            .andExpect(jsonPath("$.participants[0].id").value(yesterday.id))
            .andExpect(jsonPath("$.participants[0].informationStatus").value(false))
    }

    @Test
    fun `페이지로 나누어 참가자 id 순서로 돌려주고 합계를 알려 준다`() {
        val participants = (1..5).map { saveParticipant(EXPO_A, "참가자$it", "0101111000$it") }
        participants.forEach { attend(it, TODAY) }

        fetch(EXPO_A, "page" to "0", "size" to "2")
            .andExpect(jsonPath("$.info.totalPage").value(3))
            .andExpect(jsonPath("$.info.totalElement").value(5))
            .andExpect(jsonPath("$.participants.length()").value(2))
            .andExpect(jsonPath("$.participants[0].id").value(participants[0].id))
            .andExpect(jsonPath("$.participants[1].id").value(participants[1].id))
        fetch(EXPO_A, "page" to "2", "size" to "2")
            .andExpect(jsonPath("$.participants.length()").value(1))
            .andExpect(jsonPath("$.participants[0].id").value(participants[4].id))
        // 범위를 벗어난 페이지는 빈 목록이고 합계는 그대로다
        fetch(EXPO_A, "page" to "3", "size" to "2")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participants.length()").value(0))
            .andExpect(jsonPath("$.info.totalElement").value(5))
    }

    @Test
    fun `입장한 참가자가 없으면 합계가 0인 빈 목록이다`() {
        fetch(EXPO_A)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.info.totalPage").value(0))
            .andExpect(jsonPath("$.info.totalElement").value(0))
            .andExpect(jsonPath("$.participants.length()").value(0))
    }

    @Test
    fun `다른 박람회의 입장 기록은 포함하지 않는다`() {
        FakeExpoServer.register(EXPO_C, TODAY.minusDays(1).toString(), TODAY.plusDays(1).toString())
        attend(saveParticipant(EXPO_A, "홍길동", "01011112222"), TODAY)
        attend(saveParticipant(EXPO_C, "다른행사", "01033334444"), TODAY)

        fetch(EXPO_A).andExpect(jsonPath("$.info.totalElement").value(1)).andExpect(jsonPath("$.participants[0].name").value("홍길동"))
    }

    @Test
    fun `박람회 기간 밖의 날짜는 400이다`() {
        fetch(EXPO_A, "date" to TODAY.minusDays(2).toString()).andExpect(status().isBadRequest)
        fetch(EXPO_A, "date" to TODAY.plusDays(2).toString()).andExpect(status().isBadRequest)
        // 기간의 첫날과 마지막날은 허용한다
        fetch(EXPO_A, "date" to TODAY.minusDays(1).toString()).andExpect(status().isOk)
        fetch(EXPO_A, "date" to TODAY.plusDays(1).toString()).andExpect(status().isOk)
        // 오늘이 기간 밖인 박람회에서 date를 주지 않으면 400이다
        fetch(EXPO_B).andExpect(status().isBadRequest)
    }

    @Test
    fun `잘못된 page, size, date는 400이다`() {
        fetch(EXPO_A, "page" to "-1").andExpect(status().isBadRequest)
        fetch(EXPO_A, "size" to "0").andExpect(status().isBadRequest)
        fetch(EXPO_A, "size" to "-5").andExpect(status().isBadRequest)
        fetch(EXPO_A, "size" to "1001").andExpect(status().isBadRequest)
        fetch(EXPO_A, "page" to "abc").andExpect(status().isBadRequest)
        fetch(EXPO_A, "date" to "2026-13-40").andExpect(status().isBadRequest)
        fetch(EXPO_A, "size" to "1000").andExpect(status().isOk)
    }

    @Test
    fun `박람회가 없으면 404이다`() {
        fetch("no-such-expo").andExpect(status().isNotFound)
    }

    @Test
    fun `Expo가 실패하거나 기간 형식이 잘못되면 503이다`() {
        FakeExpoServer.failureStatus = 500
        fetch(EXPO_A).andExpect(status().isServiceUnavailable)

        FakeExpoServer.failureStatus = null
        FakeExpoServer.register(EXPO_C, "not-a-date", "also-not")
        fetch(EXPO_C).andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `토큰이 없으면 401이고 승인 전 관리자는 403이다`() {
        mockMvc.perform(get("/participant/$EXPO_A")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/participant/$EXPO_A").header("Authorization", bearerOf(pendingAdminId))).andExpect(status().isForbidden)
    }

    private fun fetch(
        expoId: String,
        vararg params: Pair<String, String>,
    ) = mockMvc.perform(
        get("/participant/$expoId").header("Authorization", bearerOf(adminId)).also { request ->
            params.forEach { (name, value) -> request.param(name, value) }
        },
    )

    private companion object {
        const val EXPO_C = "0199aaaa-0000-7000-8000-0000000000c3"
    }
}
