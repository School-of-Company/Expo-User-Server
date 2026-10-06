package team.startup.expo.query

import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManagerFactory
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.support.FakeExpoServer

class TraineeQueryTests : QueryApiTestSupport() {
    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    @Test
    fun `박람회의 연수자를 id 순서로 노션 명세의 필드만 담아 돌려준다`() {
        val first = saveTrainee(EXPO_A, "홍길동", "01011112222", "T-0001", ApplicationType.PRE)
        val second = saveTrainee(EXPO_A, "김영희", "01033334444", "T-0002", ApplicationType.FIELD)
        saveTrainee(EXPO_B, "다른행사", "01055556666", "T-0003")

        mockMvc
            .perform(get("/trainee/$EXPO_A").header("Authorization", bearerOf(adminId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(first))
            .andExpect(jsonPath("$[0].name").value("홍길동"))
            .andExpect(jsonPath("$[0].trainingId").value("T-0001"))
            .andExpect(jsonPath("$[0].phoneNumber").value("01011112222"))
            .andExpect(jsonPath("$[0].applicationType").value("PRE"))
            .andExpect(jsonPath("$[1].id").value(second))
            .andExpect(jsonPath("$[1].applicationType").value("FIELD"))
            .andExpect(jsonPath("$[0].informationJson").doesNotExist())
    }

    @Test
    fun `이름에 포함된 글자로 거르고 와일드카드 문자는 글자로 취급한다`() {
        saveTrainee(EXPO_A, "홍길동", "01011112222", "T-0001")
        saveTrainee(EXPO_A, "김길동", "01033334444", "T-0002")
        saveTrainee(EXPO_A, "이영희", "01055556666", "T-0003")
        saveTrainee(EXPO_A, "100%열정", "01077778888", "T-0004")

        searchOk("길동").andExpect(jsonPath("$.length()").value(2))
        searchOk("영희").andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("이영희"))
        searchOk("없는이름").andExpect(jsonPath("$.length()").value(0))
        // `%`가 와일드카드로 동작하면 모두 일치한다
        searchOk("%").andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("100%열정"))
        searchOk("_").andExpect(jsonPath("$.length()").value(0))
        // 이름을 비워 보내면 전체
        searchOk("").andExpect(jsonPath("$.length()").value(4))
        searchOk("   ").andExpect(jsonPath("$.length()").value(4))
    }

    @Test
    fun `연수자가 없는 박람회는 빈 배열이다`() {
        mockMvc
            .perform(get("/trainee/$EXPO_B").header("Authorization", bearerOf(adminId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `박람회가 없으면 404이다`() {
        mockMvc
            .perform(get("/trainee/no-such-expo").header("Authorization", bearerOf(adminId)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `이름이 너무 길면 400이다`() {
        search("가".repeat(51)).andExpect(status().isBadRequest)
    }

    @Test
    fun `연수자가 많아도 쿼리 수가 늘지 않는다`() {
        (1..50).forEach { saveTrainee(EXPO_A, "연수자$it", "0102%07d".format(it), "T-%04d".format(it)) }
        val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        statistics.isStatisticsEnabled = true
        statistics.clear()

        mockMvc
            .perform(get("/trainee/$EXPO_A").header("Authorization", bearerOf(adminId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(50))

        // 관리자 인증 조회 1번 + 목록 1번. 연수자 수와 무관하다
        val queries = statistics.prepareStatementCount
        check(queries <= 2) { "연수자 50명 조회에 쿼리 ${queries}번이 실행됨" }
    }

    @Test
    fun `Expo가 응답하지 않거나 인증에 실패하면 박람회 없음이 아니라 503이다`() {
        FakeExpoServer.failureStatus = 500
        mockMvc.perform(get("/trainee/$EXPO_A").header("Authorization", bearerOf(adminId))).andExpect(status().isServiceUnavailable)

        FakeExpoServer.failureStatus = 401
        mockMvc.perform(get("/trainee/$EXPO_A").header("Authorization", bearerOf(adminId))).andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `Expo에는 내부 토큰을 실어 보낸다`() {
        mockMvc.perform(get("/trainee/$EXPO_A").header("Authorization", bearerOf(adminId))).andExpect(status().isOk)

        FakeExpoServer.lastToken shouldBe FakeExpoServer.TOKEN
    }

    @Test
    fun `토큰이 없으면 401이고 승인 전 관리자는 403이며 Expo를 부르지 않는다`() {
        mockMvc.perform(get("/trainee/$EXPO_A")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/trainee/$EXPO_A").header("Authorization", bearerOf(pendingAdminId))).andExpect(status().isForbidden)

        FakeExpoServer.requestCount shouldBe 0
    }

    private fun search(name: String) =
        mockMvc.perform(get("/trainee/$EXPO_A").param("name", name).header("Authorization", bearerOf(adminId)))

    private fun searchOk(name: String) = search(name).andExpect(status().isOk)
}
