package team.startup.expo.auth

import io.jsonwebtoken.Jwts
import io.kotest.matchers.shouldBe
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
        // access token은 Expo가 받아들이는 최대 수명인 15분이다
        access.isAfter(nowUtc.plusMinutes(14)) shouldBe true
        access.isBefore(nowUtc.plusMinutes(16)) shouldBe true
        refresh.isAfter(nowUtc.plusDays(6)) shouldBe true
        refresh.isBefore(nowUtc.plusDays(8)) shouldBe true
    }

    @Test
    fun `응답의 만료 시각은 실제 JWT의 exp와 일치하고 수명은 15분을 넘지 않는다`() {
        val body = objectMapper.readTree(signIn("accepted").andReturn().response.contentAsString)

        val payload =
            Jwts
                .parser()
                .verifyWith(testKeyPair.public)
                .build()
                .parseSignedClaims(body["accessToken"].asString())
                .payload
        val expiresIn = LocalDateTime.parse(body["accessTokenExpiresIn"].asString()).toInstant(ZoneOffset.UTC)
        // 응답은 초 단위로 잘리므로 1초의 오차를 허용한다
        (kotlin.math.abs(payload.expiration.toInstant().epochSecond - expiresIn.epochSecond) <= 1) shouldBe true
        (payload.expiration.time - payload.issuedAt.time <= 15 * 60 * 1000L + 1000) shouldBe true
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
    }
}
