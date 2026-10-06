package team.startup.expo.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
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
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.Date

/**
 * 보안 규칙을 확인한다. 핸들러 유무나 요청 본문에 따라 응답 코드가 달라지므로, 열려 있는 경로는
 * 401/403이 아닌 것으로 "보안을 통과했음"을 확인한다.
 */
class AccessTokenSecurityTests : IntegrationTestSupport() {
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
    fun `로그아웃은 토큰이 없으면 401이다`() {
        mockMvc.perform(delete("/auth")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `서비스가 서명한 승인된 관리자의 토큰으로 로그아웃 경로를 통과한다`() {
        assertPassesSecurity(delete("/auth").header("Authorization", bearerOf(acceptedAdminId)))
    }

    @Test
    fun `승인 전 관리자는 403이다`() {
        mockMvc.perform(delete("/auth").header("Authorization", bearerOf(pendingAdminId))).andExpect(status().isForbidden)
    }

    @Test
    fun `X-User-Role 헤더는 무시하고 DB의 권한을 쓴다`() {
        mockMvc
            .perform(delete("/auth").header("Authorization", bearerOf(pendingAdminId)).header("X-User-Role", "ROLE_ADMIN"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `존재하지 않는 관리자의 토큰은 401이다`() {
        mockMvc.perform(delete("/auth").header("Authorization", bearerOf(99999))).andExpect(status().isUnauthorized)
    }

    // --- 직접 접속한 쪽이 관리자 행세를 할 수 없다

    @Test
    fun `X-User-Id 헤더만으로는 관리자 경로에 접근할 수 없다`() {
        mockMvc.perform(get("/admin/my").header("X-User-Id", acceptedAdminId)).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/admin").header("X-User-Id", acceptedAdminId)).andExpect(status().isUnauthorized)
        mockMvc.perform(delete("/admin").header("X-User-Id", acceptedAdminId)).andExpect(status().isUnauthorized)
        mockMvc.perform(delete("/auth").header("X-User-Id", acceptedAdminId)).andExpect(status().isUnauthorized)
        adminRepository.existsById(acceptedAdminId).let { check(it) { "헤더만으로 탈퇴가 처리되면 안 된다" } }
    }

    @Test
    fun `토큰과 X-User-Id가 다르면 토큰의 관리자로 판단한다`() {
        mockMvc
            .perform(get("/admin/my").header("Authorization", bearerOf(pendingAdminId)).header("X-User-Id", acceptedAdminId))
            .andExpect(status().isForbidden)
        mockMvc
            .perform(get("/admin/my").header("Authorization", bearerOf(acceptedAdminId)).header("X-User-Id", pendingAdminId))
            .andExpect(status().isOk)
    }

    @Test
    fun `다른 키로 서명한 토큰은 401이다`() {
        val token = sign(newKeyPair(), acceptedAdminId.toString())

        mockMvc.perform(get("/admin/my").header("Authorization", "Bearer $token")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `만료된 토큰은 401이다`() {
        val token = sign(testKeyPair, acceptedAdminId.toString(), expiresAt = Date(System.currentTimeMillis() - 60_000))

        mockMvc.perform(get("/admin/my").header("Authorization", "Bearer $token")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `알고리즘을 바꿔 위조한 토큰은 401이다`() {
        // 공개키를 HMAC 비밀키처럼 써서 서명한 토큰(알고리즘 혼동 공격)
        val hmacForged =
            Jwts
                .builder()
                .subject(acceptedAdminId.toString())
                .expiration(Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(testKeyPair.public.encoded))
                .compact()
        // 서명이 없는 토큰(alg=none)
        val unsigned =
            Jwts
                .builder()
                .subject(acceptedAdminId.toString())
                .expiration(Date(System.currentTimeMillis() + 60_000))
                .compact()

        listOf(hmacForged, unsigned).forEach {
            mockMvc.perform(get("/admin/my").header("Authorization", "Bearer $it")).andExpect(status().isUnauthorized)
        }
    }

    @Test
    fun `sub가 숫자가 아닌 토큰과 형식이 틀린 값은 401이다`() {
        val nonNumericSub = sign(testKeyPair, "not-a-number")

        mockMvc.perform(get("/admin/my").header("Authorization", "Bearer $nonNumericSub")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/admin/my").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/admin/my").header("Authorization", "Bearer ")).andExpect(status().isUnauthorized)
        mockMvc
            .perform(get("/admin/my").header("Authorization", bearerOf(acceptedAdminId).removePrefix("Bearer ")))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `refresh token은 access token으로 쓸 수 없다`() {
        mockMvc
            .perform(
                get("/admin/my").header("Authorization", "Bearer $acceptedAdminId.abcdefghijklmnop"),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `명시하지 않은 경로는 막혀 있다`() {
        mockMvc.perform(get("/auth")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/not-defined").header("Authorization", bearerOf(acceptedAdminId))).andExpect(status().isForbidden)
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

    private fun sign(
        keyPair: KeyPair,
        subject: String,
        expiresAt: Date = Date(System.currentTimeMillis() + 60_000),
    ): String =
        Jwts
            .builder()
            .subject(subject)
            .expiration(expiresAt)
            .signWith(keyPair.private, Jwts.SIG.RS256)
            .compact()

    private fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

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
