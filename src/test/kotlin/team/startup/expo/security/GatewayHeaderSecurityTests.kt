package team.startup.expo.security

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.support.IntegrationTestSupport

/**
 * 보안 규칙만 확인한다. 핸들러 유무나 요청 본문에 따라 응답 코드가 달라지므로, 열려 있는 경로는
 * 401/403이 아닌 것으로 "보안을 통과했음"을 확인한다.
 */
class GatewayHeaderSecurityTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var adminRepository: AdminRepository

    private var acceptedAdminId = 0L
    private var pendingAdminId = 0L

    @BeforeEach
    fun setUp() {
        clearAdmins()
        acceptedAdminId = adminRepository.save(admin("accepted", "01011110001").apply { accept() }).id!!
        pendingAdminId = adminRepository.save(admin("pending", "01011110002")).id!!
    }

    @Test
    fun `가입, 로그인, 재발급 경로는 인증 없이 열려 있다`() {
        assertPassesSecurity(post("/auth"))
        assertPassesSecurity(post("/auth/signin"))
        assertPassesSecurity(patch("/auth"))
    }

    @Test
    fun `로그아웃은 헤더가 없으면 401이다`() {
        mockMvc.perform(delete("/auth")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `승인된 관리자의 X-User-Id로 로그아웃 경로를 통과한다`() {
        assertPassesSecurity(delete("/auth").header("X-User-Id", acceptedAdminId))
    }

    @Test
    fun `승인 전 관리자는 403이다`() {
        mockMvc.perform(delete("/auth").header("X-User-Id", pendingAdminId)).andExpect(status().isForbidden)
    }

    @Test
    fun `X-User-Role 헤더는 무시하고 DB의 권한을 쓴다`() {
        mockMvc
            .perform(delete("/auth").header("X-User-Id", pendingAdminId).header("X-User-Role", "ROLE_ADMIN"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `존재하지 않는 관리자나 숫자가 아닌 값은 401이다`() {
        mockMvc.perform(delete("/auth").header("X-User-Id", 99999)).andExpect(status().isUnauthorized)
        mockMvc.perform(delete("/auth").header("X-User-Id", "not-a-number")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `명시하지 않은 경로는 막혀 있다`() {
        mockMvc.perform(get("/auth")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/not-defined").header("X-User-Id", acceptedAdminId)).andExpect(status().isForbidden)
    }

    @Test
    fun `actuator health는 열려 있다`() {
        val result =
            mockMvc
                .perform(get("/actuator/health"))
                .andReturn()
                .response.status
        check(result != 401 && result != 403) { "health must be permitted, was $result" }
    }

    private fun assertPassesSecurity(request: MockHttpServletRequestBuilder) {
        val result =
            mockMvc
                .perform(request)
                .andReturn()
                .response.status
        check(result != 401 && result != 403) { "request must pass security, was $result" }
    }

    private fun admin(
        nickname: String,
        phoneNumber: String,
    ) = Admin(
        name = "관리자",
        nickname = nickname,
        email = "$nickname@gsm.hs.kr",
        password = "encoded",
        phoneNumber = phoneNumber,
    )
}
