package team.startup.expo.auth

import io.jsonwebtoken.Jwts
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ReissueTokenTests : AuthFlowTestSupport() {
    @Test
    fun `유효한 refresh token으로 새 토큰 쌍을 받고 access token은 현재 관리자를 가리킨다`() {
        val first = signInAsAccepted()

        val result = reissue(first.refreshToken).andExpect(status().isOk).andReturn()
        val body = objectMapper.readTree(result.response.contentAsString)

        val jws =
            Jwts
                .parser()
                .verifyWith(testKeyPair.public)
                .build()
                .parseSignedClaims(body["accessToken"].asString())
        jws.payload.subject shouldBe acceptedAdminId.toString()
        jws.payload["role"] shouldBe "ROLE_ADMIN"
        (body["refreshToken"].asString() == first.refreshToken) shouldBe false
        body["accessTokenExpiresIn"].asString().isNotBlank() shouldBe true
        body["refreshTokenExpiresIn"].asString().isNotBlank() shouldBe true
    }

    @Test
    fun `재발급하면 이전 refresh token은 쓸 수 없고 새 토큰은 쓸 수 있다`() {
        val first = signInAsAccepted()
        val second = objectMapper.readTree(reissue(first.refreshToken).andReturn().response.contentAsString)["refreshToken"].asString()

        reissue(first.refreshToken).andExpect(status().isUnauthorized)
        reissue(second).andExpect(status().isOk)
    }

    @Test
    fun `저장되지 않은 토큰은 401이다`() {
        signInAsAccepted()

        reissue("never-issued-token").andExpect(status().isUnauthorized)
    }

    @Test
    fun `access token은 refresh token으로 쓸 수 없다`() {
        val tokens = signInAsAccepted()

        reissue(tokens.accessToken).andExpect(status().isUnauthorized)
    }

    @Test
    fun `Bearer 접두사가 없으면 401이다`() {
        val tokens = signInAsAccepted()

        mockMvc.perform(patch("/auth").header("RefreshToken", tokens.refreshToken)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `헤더가 없으면 400이다`() {
        mockMvc.perform(patch("/auth")).andExpect(status().isBadRequest)
    }

    @Test
    fun `탈퇴한 관리자의 refresh token은 401이고 정리된다`() {
        val tokens = signInAsAccepted()
        adminRepository.deleteById(acceptedAdminId)

        reissue(tokens.refreshToken).andExpect(status().isUnauthorized)
        reissue(tokens.refreshToken).andExpect(status().isUnauthorized)
    }

    private fun reissue(refreshToken: String) = mockMvc.perform(patch("/auth").header("RefreshToken", "Bearer $refreshToken"))
}
