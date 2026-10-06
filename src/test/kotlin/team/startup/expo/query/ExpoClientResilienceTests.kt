package team.startup.expo.query

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.support.FakeExpoServer

/** Expo 호출 장애가 "박람회 없음"으로 보이지 않고, 반복 실패하면 Expo를 부르지 않고 바로 실패하는지 확인한다. */
class ExpoClientResilienceTests : QueryApiTestSupport() {
    @Autowired
    private lateinit var expoCircuitBreaker: CircuitBreaker

    @Test
    fun `없는 박람회를 계속 조회해도 회로가 열리지 않는다`() {
        repeat(30) {
            mockMvc.perform(get("/trainee/no-such-expo-$it").header("Authorization", bearerOf(adminId))).andExpect(status().isNotFound)
        }

        expoCircuitBreaker.state shouldBe CircuitBreaker.State.CLOSED
        mockMvc.perform(get("/trainee/$EXPO_A").header("Authorization", bearerOf(adminId))).andExpect(status().isOk)
    }

    @Test
    fun `Expo가 계속 실패하면 회로가 열려 Expo를 부르지 않고 503으로 바로 실패한다`() {
        FakeExpoServer.failureStatus = 500

        repeat(10) {
            mockMvc.perform(get("/trainee/$EXPO_A").header("Authorization", bearerOf(adminId))).andExpect(status().isServiceUnavailable)
        }
        expoCircuitBreaker.state shouldBe CircuitBreaker.State.OPEN

        val before = FakeExpoServer.requestCount
        mockMvc.perform(get("/trainee/$EXPO_A").header("Authorization", bearerOf(adminId))).andExpect(status().isServiceUnavailable)
        FakeExpoServer.requestCount shouldBe before
    }

    @Test
    fun `내부 토큰이 맞지 않아 인증에 실패해도 박람회 없음이 아니라 503이다`() {
        FakeExpoServer.failureStatus = 401

        mockMvc.perform(get("/participant/$EXPO_A").header("Authorization", bearerOf(adminId))).andExpect(status().isServiceUnavailable)
    }
}
