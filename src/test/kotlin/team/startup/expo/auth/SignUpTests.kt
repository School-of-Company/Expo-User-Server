package team.startup.expo.auth

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.user.entity.Authority
import team.startup.expo.domain.user.entity.Status
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.support.IntegrationTestSupport

class SignUpTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @BeforeEach
    fun setUp() {
        clearAdmins()
    }

    @Test
    fun `가입하면 201이고 승인 대기 상태로 암호화된 비밀번호와 함께 저장된다`() {
        signUp().andExpect(status().isCreated)

        val admin = adminRepository.findByNickname("admin1")!!
        admin.status shouldBe Status.PENDING
        admin.authority shouldBe Authority.ROLE_STANDARD
        admin.phoneNumber shouldBe "01012341234"
        (admin.password == PASSWORD) shouldBe false
        passwordEncoder.matches(PASSWORD, admin.password) shouldBe true
    }

    @Test
    fun `전화번호 이메일 닉네임이 중복이면 409이고 전화번호를 먼저 확인한다`() {
        signUp().andExpect(status().isCreated)

        signUp(nickname = "other", email = "other@gsm.hs.kr")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("이미 존재하는 전화번호입니다."))
        signUp(nickname = "other", phoneNumber = "01099999999")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("이미 존재하는 이메일입니다."))
        signUp(email = "other@gsm.hs.kr", phoneNumber = "01099999999")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("이미 존재하는 닉네임입니다."))
        adminRepository.count() shouldBe 1L
    }

    @Test
    fun `비밀번호 정책을 만족하지 않으면 400이다`() {
        listOf("Sh1!aB7", "alllowercase1!", "ALLUPPERCASE1!", "NoDigits!!!", "NoSpecial1234", "Aa1!" + "x".repeat(21))
            .forEach { signUp(password = it).andExpect(status().isBadRequest) }
        adminRepository.count() shouldBe 0L
    }

    @Test
    fun `전화번호는 하이픈 없는 숫자만 허용한다`() {
        signUp(phoneNumber = "010-1234-1234").andExpect(status().isBadRequest)
        signUp(phoneNumber = "0212341234").andExpect(status().isBadRequest)
        signUp(phoneNumber = "010123412345678").andExpect(status().isBadRequest)
        adminRepository.count() shouldBe 0L
    }

    @Test
    fun `이메일 형식과 필수 값을 검증한다`() {
        signUp(email = "not-an-email").andExpect(status().isBadRequest)
        signUp(name = " ").andExpect(status().isBadRequest)
        signUp(nickname = "").andExpect(status().isBadRequest)
        mockMvc
            .perform(post("/auth").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest)
        adminRepository.count() shouldBe 0L
    }

    private fun signUp(
        name: String = "관리자",
        nickname: String = "admin1",
        email: String = "admin1@gsm.hs.kr",
        password: String = PASSWORD,
        phoneNumber: String = "01012341234",
    ) = mockMvc.perform(
        post("/auth")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """{"name":"$name","nickname":"$nickname","email":"$email","password":"$password","phoneNumber":"$phoneNumber"}""",
            ),
    )

    private companion object {
        const val PASSWORD = "Passw0rd!"
    }
}
