package team.startup.expo.auth

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.support.IntegrationTestSupport
import tools.jackson.databind.ObjectMapper

/** 승인된 관리자와 승인 전 관리자를 하나씩 만들고, 로그인으로 토큰을 받는 헬퍼를 제공한다. */
abstract class AuthFlowTestSupport : IntegrationTestSupport() {
    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var adminRepository: AdminRepository

    @Autowired
    protected lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    protected var acceptedAdminId = 0L
    protected var pendingAdminId = 0L

    @BeforeEach
    fun setUpAdmins() {
        clearAdmins()
        acceptedAdminId = adminRepository.save(admin("accepted", "01011110001").apply { accept() }).id!!
        pendingAdminId = adminRepository.save(admin("pending", "01011110002")).id!!
    }

    protected data class Tokens(
        val accessToken: String,
        val refreshToken: String,
    )

    protected fun signInAsAccepted(): Tokens {
        val response =
            mockMvc
                .perform(
                    post("/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"nickname":"accepted","password":"$PASSWORD"}"""),
                ).andReturn()
                .response.contentAsString
        val body = objectMapper.readTree(response)
        return Tokens(body["accessToken"].asString(), body["refreshToken"].asString())
    }

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

    protected companion object {
        const val PASSWORD = "Passw0rd!"
    }
}
