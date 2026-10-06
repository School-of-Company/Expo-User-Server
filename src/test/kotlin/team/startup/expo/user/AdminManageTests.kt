package team.startup.expo.user

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.auth.service.RefreshTokenService
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.entity.Authority
import team.startup.expo.domain.user.entity.Status
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.support.IntegrationTestSupport

class AdminManageTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var refreshTokenService: RefreshTokenService

    private var acceptedAdminId = 0L
    private var otherAcceptedAdminId = 0L
    private var pendingAdminId = 0L

    @BeforeEach
    fun setUp() {
        clearAdmins()
        acceptedAdminId = adminRepository.save(admin("accepted", "01011110001").apply { accept() }).id!!
        otherAcceptedAdminId = adminRepository.save(admin("other", "01011110004").apply { accept() }).id!!
        pendingAdminId = adminRepository.save(admin("pending", "01011110002")).id!!
    }

    @Test
    fun `승인하면 204이고 승인된 관리자는 관리자 API를 쓸 수 있다`() {
        mockMvc.perform(get("/admin/my").header("X-User-Id", pendingAdminId)).andExpect(status().isForbidden)

        mockMvc.perform(patch("/admin/$pendingAdminId").header("X-User-Id", acceptedAdminId)).andExpect(status().isNoContent)

        val accepted = adminRepository.findById(pendingAdminId).get()
        accepted.status shouldBe Status.ACCEPTED
        accepted.authority shouldBe Authority.ROLE_ADMIN
        mockMvc.perform(get("/admin/my").header("X-User-Id", pendingAdminId)).andExpect(status().isOk)
    }

    @Test
    fun `이미 승인된 관리자를 다시 승인해도 204이다`() {
        mockMvc.perform(patch("/admin/$otherAcceptedAdminId").header("X-User-Id", acceptedAdminId)).andExpect(status().isNoContent)

        adminRepository.findById(otherAcceptedAdminId).get().status shouldBe Status.ACCEPTED
    }

    @Test
    fun `없는 관리자를 승인하면 404이다`() {
        mockMvc.perform(patch("/admin/99999").header("X-User-Id", acceptedAdminId)).andExpect(status().isNotFound)
    }

    @Test
    fun `승인 대기 관리자를 거절하면 204이고 삭제된다`() {
        mockMvc.perform(delete("/admin/$pendingAdminId").header("X-User-Id", acceptedAdminId)).andExpect(status().isNoContent)

        adminRepository.existsById(pendingAdminId) shouldBe false
    }

    @Test
    fun `이미 승인된 관리자를 거절하면 409이고 삭제되지 않는다`() {
        mockMvc
            .perform(delete("/admin/$otherAcceptedAdminId").header("X-User-Id", acceptedAdminId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("이미 수락한 유저입니다."))

        adminRepository.existsById(otherAcceptedAdminId) shouldBe true
    }

    @Test
    fun `없는 관리자를 거절하면 404이다`() {
        mockMvc.perform(delete("/admin/99999").header("X-User-Id", acceptedAdminId)).andExpect(status().isNotFound)
    }

    @Test
    fun `탈퇴하면 204이고 본인만 삭제되며 refresh token도 폐기된다`() {
        val refresh = refreshTokenService.issue(acceptedAdminId)
        val otherRefresh = refreshTokenService.issue(otherAcceptedAdminId)

        mockMvc.perform(delete("/admin").header("X-User-Id", acceptedAdminId)).andExpect(status().isNoContent)

        adminRepository.existsById(acceptedAdminId) shouldBe false
        adminRepository.existsById(otherAcceptedAdminId) shouldBe true
        refreshTokenService.findAdminId(refresh.value) shouldBe null
        refreshTokenService.findAdminId(otherRefresh.value) shouldBe otherAcceptedAdminId
    }

    @Test
    fun `탈퇴한 관리자의 헤더는 이후 401이다`() {
        mockMvc.perform(delete("/admin").header("X-User-Id", acceptedAdminId)).andExpect(status().isNoContent)

        mockMvc.perform(get("/admin/my").header("X-User-Id", acceptedAdminId)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `승인 전 관리자는 403이고 헤더가 없으면 401이다`() {
        mockMvc.perform(patch("/admin/$otherAcceptedAdminId").header("X-User-Id", pendingAdminId)).andExpect(status().isForbidden)
        mockMvc.perform(delete("/admin/$otherAcceptedAdminId").header("X-User-Id", pendingAdminId)).andExpect(status().isForbidden)
        mockMvc.perform(delete("/admin").header("X-User-Id", pendingAdminId)).andExpect(status().isForbidden)

        mockMvc.perform(patch("/admin/$pendingAdminId")).andExpect(status().isUnauthorized)
        mockMvc.perform(delete("/admin/$pendingAdminId")).andExpect(status().isUnauthorized)
        mockMvc.perform(delete("/admin")).andExpect(status().isUnauthorized)
        adminRepository.count() shouldBe 3L
    }

    @Test
    fun `숫자가 아닌 관리자 id는 400이다`() {
        mockMvc.perform(patch("/admin/abc").header("X-User-Id", acceptedAdminId)).andExpect(status().isBadRequest)
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
