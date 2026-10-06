package team.startup.expo.auth

import io.jsonwebtoken.Jwts
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.matchesPattern
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.auth.service.RefreshTokenService
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.support.IntegrationTestSupport
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.time.ZoneOffset

class SignInTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var refreshTokenService: RefreshTokenService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private var acceptedAdminId = 0L

    @BeforeEach
    fun setUp() {
        clearAdmins()
        acceptedAdminId = adminRepository.save(admin("accepted", "01011110001").apply { accept() }).id!!
        adminRepository.save(admin("pending", "01011110002"))
    }

    @Test
    fun `승인된 관리자는 토큰을 받고 access token은 RS256으로 서명된다`() {
        val result = signIn("accepted").andExpect(status().isOk).andReturn()
        val body = objectMapper.readTree(result.response.contentAsString)

        val jws =
            Jwts
                .parser()
                .verifyWith(testKeyPair.public)
                .build()
                .parseSignedClaims(body["accessToken"].asString())
        jws.header["alg"] shouldBe "RS256"
        jws.payload.subject shouldBe acceptedAdminId.toString()
        jws.payload["role"] shouldBe "ROLE_ADMIN"
        refreshTokenService.findAdminId(body["refreshToken"].asString()) shouldBe acceptedAdminId
    }

    @Test
    fun `만료 시각은 UTC 기준의 오프셋 없는 값이다`() {
        val body = objectMapper.readTree(signIn("accepted").andReturn().response.contentAsString)
        val nowUtc = LocalDateTime.now(ZoneOffset.UTC)

        val access = LocalDateTime.parse(body["accessTokenExpiresIn"].asString())
        val refresh = LocalDateTime.parse(body["refreshTokenExpiresIn"].asString())
        access.isAfter(nowUtc.plusHours(23)) shouldBe true
        access.isBefore(nowUtc.plusHours(25)) shouldBe true
        refresh.isAfter(nowUtc.plusDays(6)) shouldBe true
        refresh.isBefore(nowUtc.plusDays(8)) shouldBe true
    }

    @Test
    fun `만료 시각은 노션 형식이고 토큰에는 Bearer 접두사가 없다`() {
        signIn("accepted")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessTokenExpiresIn").value(matchesPattern(EXPIRES_IN_PATTERN)))
            .andExpect(jsonPath("$.refreshTokenExpiresIn").value(matchesPattern(EXPIRES_IN_PATTERN)))
            .andExpect(jsonPath("$.accessToken").value(not(startsWith("Bearer "))))
            .andExpect(jsonPath("$.refreshToken").value(not(startsWith("Bearer "))))
    }

    @Test
    fun `닉네임이 없으면 404이다`() {
        signIn("nobody").andExpect(status().isNotFound)
    }

    @Test
    fun `비밀번호가 틀리면 400이다`() {
        signIn("accepted", password = "Wrong1234!").andExpect(status().isBadRequest)
    }

    @Test
    fun `승인 전 관리자는 비밀번호가 맞아도 403이고 토큰을 받지 못한다`() {
        signIn("pending").andExpect(status().isForbidden).andExpect(jsonPath("$.accessToken").doesNotExist())
    }

    @Test
    fun `다시 로그인하면 이전 refresh token은 무효가 된다`() {
        val first = refreshTokenOf(signIn("accepted").andReturn())
        val second = refreshTokenOf(signIn("accepted").andReturn())

        refreshTokenService.findAdminId(first) shouldBe null
        refreshTokenService.findAdminId(second) shouldBe acceptedAdminId
    }

    @Test
    fun `필수 값이 없으면 400이다`() {
        mockMvc
            .perform(post("/auth/signin").contentType(MediaType.APPLICATION_JSON).content("""{"nickname":"accepted"}"""))
            .andExpect(status().isBadRequest)
    }

    private fun refreshTokenOf(result: MvcResult): String =
        objectMapper.readTree(result.response.contentAsString)["refreshToken"].asString()

    private fun signIn(
        nickname: String,
        password: String = PASSWORD,
    ) = mockMvc.perform(
        post("/auth/signin")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"nickname":"$nickname","password":"$password"}"""),
    )

    private fun admin(
        nickname: String,
        phoneNumber: String,
    ) = Admin(
        name = "관리자",
        nickname = nickname,
        email = "$nickname@gsm.hs.kr",
        password = requireNotNull(passwordEncoder.encode(PASSWORD)),
        phoneNumber = phoneNumber,
    )

    private companion object {
        const val PASSWORD = "Passw0rd!"

        // 노션 명세: yyyy-MM-dd'T'HH:mm:ss (소수점 초 없음)
        const val EXPIRES_IN_PATTERN = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}$"
    }
}
