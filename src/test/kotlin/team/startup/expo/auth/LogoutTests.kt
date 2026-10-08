package team.startup.expo.auth

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class LogoutTests : AuthFlowTestSupport() {
    @Test
    fun `로그아웃하면 204이고 refresh token으로 재발급할 수 없다`() {
        val tokens = signInAsAccepted()

        delete("/auth").header("Authorization", bearerOf(acceptedAdminId)).let { mockMvc.perform(it) }.andExpect(status().isNoContent)

        mockMvc
            .perform(patch("/auth").header("RefreshToken", "Bearer ${tokens.refreshToken}"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `이미 로그아웃했거나 로그인한 적 없어도 204이다`() {
        mockMvc.perform(delete("/auth").header("Authorization", bearerOf(acceptedAdminId))).andExpect(status().isNoContent)
        mockMvc.perform(delete("/auth").header("Authorization", bearerOf(acceptedAdminId))).andExpect(status().isNoContent)
    }

    @Test
    fun `헤더가 없으면 401이다`() {
        mockMvc.perform(delete("/auth")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `승인 전 관리자는 403이다`() {
        mockMvc.perform(delete("/auth").header("Authorization", bearerOf(pendingAdminId))).andExpect(status().isForbidden)
    }

    @Test
    fun `존재하지 않는 관리자는 401이다`() {
        mockMvc.perform(delete("/auth").header("Authorization", bearerOf(99999))).andExpect(status().isUnauthorized)
    }

    @Test
    fun `다른 관리자의 refresh token은 폐기하지 않는다`() {
        val tokens = signInAsAccepted()

        mockMvc.perform(delete("/auth").header("Authorization", bearerOf(pendingAdminId))).andExpect(status().isForbidden)

        mockMvc
            .perform(patch("/auth").header("RefreshToken", "Bearer ${tokens.refreshToken}"))
            .andExpect(status().isOk)
    }
}
