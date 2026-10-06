package team.startup.expo.user

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.support.IntegrationTestSupport

class AdminReadTests : IntegrationTestSupport() {
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
        pendingAdminId = adminRepository.save(admin("pending1", "01011110002")).id!!
        adminRepository.save(admin("pending2", "01011110003"))
    }

    @Test
    fun `내 정보는 이름 닉네임 이메일만 돌려준다`() {
        mockMvc
            .perform(get("/admin/my").header("X-User-Id", acceptedAdminId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("관리자"))
            .andExpect(jsonPath("$.nickname").value("accepted"))
            .andExpect(jsonPath("$.email").value("accepted@gsm.hs.kr"))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.phoneNumber").doesNotExist())
    }

    @Test
    fun `승인 대기 목록에는 대기 중인 관리자만 있고 비밀번호는 없다`() {
        mockMvc
            .perform(get("/admin").header("X-User-Id", acceptedAdminId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.nickname=='accepted')]").isEmpty)
            .andExpect(jsonPath("$[?(@.nickname=='pending1')].phoneNumber").value("01011110002"))
            .andExpect(jsonPath("$[0].id").exists())
            .andExpect(jsonPath("$[0].password").doesNotExist())
    }

    @Test
    fun `대기 중인 관리자가 없으면 빈 배열이다`() {
        jdbcTemplate.execute("DELETE FROM tb_admin WHERE status = 'PENDING'")

        mockMvc
            .perform(get("/admin").header("X-User-Id", acceptedAdminId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `헤더가 없으면 401이다`() {
        mockMvc.perform(get("/admin/my")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/admin")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `승인 전 관리자는 403이다`() {
        mockMvc.perform(get("/admin/my").header("X-User-Id", pendingAdminId)).andExpect(status().isForbidden)
        mockMvc.perform(get("/admin").header("X-User-Id", pendingAdminId)).andExpect(status().isForbidden)
    }

    @Test
    fun `존재하지 않는 관리자는 401이다`() {
        mockMvc.perform(get("/admin/my").header("X-User-Id", 99999)).andExpect(status().isUnauthorized)
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
