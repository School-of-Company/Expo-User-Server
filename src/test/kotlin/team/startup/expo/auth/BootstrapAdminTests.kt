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
    fun `설정한 닉네임으로 가입해도 가입 직후에는 승인하지 않고 승인 대기다`() {
        signUp("first-admin", "first@gsm.hs.kr", "01011110001").andExpect(status().isCreated)

        val admin = adminRepository.findByNickname("first-admin")!!
        admin.status shouldBe Status.PENDING
        admin.authority shouldBe Authority.ROLE_STANDARD
        signIn("first-admin").andExpect(status().isForbidden)
    }

    @Test
    fun `기동할 때 설정한 닉네임의 승인 대기 계정을 관리자로 승인하고 그 계정으로 로그인한다`() {
        signUp("first-admin", "first@gsm.hs.kr", "01011110001").andExpect(status().isCreated)

        runner.run(DefaultApplicationArguments())

        val admin = adminRepository.findByNickname("first-admin")!!
        admin.status shouldBe Status.ACCEPTED
        admin.authority shouldBe Authority.ROLE_ADMIN
        signIn("first-admin").andExpect(status().isOk)
    }

    @Test
    fun `설정하지 않은 닉네임의 승인 대기 계정은 승인하지 않는다`() {
        adminRepository.save(admin("first-admin", "first@gsm.hs.kr", "01011110001"))
        adminRepository.save(admin("other", "other@gsm.hs.kr", "01011110002"))

        runner.run(DefaultApplicationArguments())

        adminRepository.findByNickname("first-admin")!!.status shouldBe Status.ACCEPTED
        val other = adminRepository.findByNickname("other")!!
        other.status shouldBe Status.PENDING
        other.authority shouldBe Authority.ROLE_STANDARD
        signIn("other").andExpect(status().isForbidden)
    }

    @Test
    fun `기동할 때 승인된 관리자가 이미 있으면 아무것도 하지 않는다`() {
        adminRepository.save(admin("existing", "existing@gsm.hs.kr", "01011110003").apply { accept() })
        adminRepository.save(admin("first-admin", "first@gsm.hs.kr", "01011110001"))

        runner.run(DefaultApplicationArguments())

        adminRepository.findByNickname("first-admin")!!.status shouldBe Status.PENDING
    }

    @Test
    fun `설정한 닉네임의 계정이 없으면 아무것도 하지 않고 기동은 계속된다`() {
        adminRepository.save(admin("other", "other@gsm.hs.kr", "01011110002"))

        runner.run(DefaultApplicationArguments())

        adminRepository.findByNickname("other")!!.status shouldBe Status.PENDING
        adminRepository.count() shouldBe 1L
    }

    @Test
    fun `여러 번 기동해도 결과가 같고 나중에 가입한 계정은 승인하지 않는다`() {
        adminRepository.save(admin("first-admin", "first@gsm.hs.kr", "01011110001"))

        repeat(2) { runner.run(DefaultApplicationArguments()) }
        // 첫 관리자가 생긴 뒤에는 설정이 남아 있어도 다른 계정을 승인하지 않는다
        adminRepository.save(admin("late", "late@gsm.hs.kr", "01011110002"))
        runner.run(DefaultApplicationArguments())

        adminRepository.findByNickname("first-admin")!!.status shouldBe Status.ACCEPTED
        adminRepository.findByNickname("late")!!.status shouldBe Status.PENDING
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
