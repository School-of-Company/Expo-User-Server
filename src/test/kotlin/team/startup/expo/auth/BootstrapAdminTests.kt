package team.startup.expo.auth

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.entity.Authority
import team.startup.expo.domain.user.entity.Status
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.global.security.BootstrapAdminRunner
import team.startup.expo.support.IntegrationTestSupport

@TestPropertySource(properties = ["bootstrap-admin.nickname=first-admin"])
class BootstrapAdminTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var runner: BootstrapAdminRunner

    @BeforeEach
    fun setUp() {
        clearAdmins()
    }

    @Test
    fun `승인된 관리자가 없을 때 설정한 닉네임으로 가입하면 바로 관리자가 되어 로그인할 수 있다`() {
        signUp("first-admin", "first@gsm.hs.kr", "01011110001").andExpect(status().isCreated)

        val admin = adminRepository.findByNickname("first-admin")!!
        admin.status shouldBe Status.ACCEPTED
        admin.authority shouldBe Authority.ROLE_ADMIN
        signIn("first-admin").andExpect(status().isOk)
    }

    @Test
    fun `설정하지 않은 닉네임은 평소처럼 승인 대기다`() {
        signUp("someone", "someone@gsm.hs.kr", "01011110002").andExpect(status().isCreated)

        val admin = adminRepository.findByNickname("someone")!!
        admin.status shouldBe Status.PENDING
        admin.authority shouldBe Authority.ROLE_STANDARD
        signIn("someone").andExpect(status().isForbidden)
    }

    @Test
    fun `이미 승인된 관리자가 있으면 설정한 닉네임으로 가입해도 승인 대기다`() {
        adminRepository.save(admin("existing", "existing@gsm.hs.kr", "01011110003").apply { accept() })

        signUp("first-admin", "first@gsm.hs.kr", "01011110001").andExpect(status().isCreated)

        adminRepository.findByNickname("first-admin")!!.status shouldBe Status.PENDING
    }

    @Test
    fun `기동할 때 설정한 닉네임으로 이미 가입해 둔 계정을 승인한다`() {
        adminRepository.save(admin("first-admin", "first@gsm.hs.kr", "01011110001"))
        adminRepository.save(admin("other", "other@gsm.hs.kr", "01011110002"))

        runner.run(DefaultApplicationArguments())

        adminRepository.findByNickname("first-admin")!!.status shouldBe Status.ACCEPTED
        adminRepository.findByNickname("other")!!.status shouldBe Status.PENDING
    }

    @Test
    fun `기동할 때 승인된 관리자가 이미 있으면 아무것도 하지 않는다`() {
        adminRepository.save(admin("existing", "existing@gsm.hs.kr", "01011110003").apply { accept() })
        adminRepository.save(admin("first-admin", "first@gsm.hs.kr", "01011110001"))

        runner.run(DefaultApplicationArguments())

        adminRepository.findByNickname("first-admin")!!.status shouldBe Status.PENDING
    }

    private fun admin(
        nickname: String,
        email: String,
        phoneNumber: String,
    ) = Admin(
        name = "관리자",
        nickname = nickname,
        email = email,
        password = requireNotNull(passwordEncoder.encode(PASSWORD)),
        phoneNumber = phoneNumber,
    )

    private fun signUp(
        nickname: String,
        email: String,
        phoneNumber: String,
    ) = mockMvc.perform(
        post("/auth")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """{"name":"관리자","nickname":"$nickname","email":"$email","password":"$PASSWORD","phoneNumber":"$phoneNumber"}""",
            ),
    )

    private fun signIn(nickname: String) =
        mockMvc.perform(
            post("/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nickname":"$nickname","password":"$PASSWORD"}"""),
        )

    private companion object {
        const val PASSWORD = "Passw0rd!"
    }
}
