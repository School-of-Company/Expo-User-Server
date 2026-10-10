package team.startup.expo.auth

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.security.crypto.password.PasswordEncoder
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.entity.Status
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.global.security.BootstrapAdminRunner
import team.startup.expo.support.IntegrationTestSupport

/** `BOOTSTRAP_ADMIN_NICKNAME`을 설정하지 않은 기본 상태에서는 기동해도 어떤 계정도 승인하지 않는다. */
class BootstrapAdminDisabledTests : IntegrationTestSupport() {
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
    fun `설정하지 않으면 승인 대기 계정이 있어도 아무것도 승인하지 않는다`() {
        adminRepository.save(
            Admin(
                name = "관리자",
                nickname = "first-admin",
                email = "first@gsm.hs.kr",
                password = requireNotNull(passwordEncoder.encode("Passw0rd!")),
                phoneNumber = "01011110001",
            ),
        )

        runner.run(DefaultApplicationArguments())

        adminRepository.findByNickname("first-admin")!!.status shouldBe Status.PENDING
    }
}
