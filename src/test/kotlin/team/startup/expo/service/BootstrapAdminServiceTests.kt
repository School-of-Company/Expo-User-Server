package team.startup.expo.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.entity.Authority
import team.startup.expo.domain.user.entity.Status
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.domain.user.service.impl.BootstrapAdminServiceImpl
import team.startup.expo.global.security.BootstrapAdminProperties

class BootstrapAdminServiceTests :
    BehaviorSpec({
        fun admin(status: Status = Status.PENDING) =
            Admin(
                id = 1L,
                name = "관리자",
                nickname = "first-admin",
                email = "first@test.com",
                password = "encoded",
                phoneNumber = "01012345678",
                status = status,
            )

        fun service(
            nickname: String,
            repository: AdminRepository,
        ) = BootstrapAdminServiceImpl(BootstrapAdminProperties(nickname), repository)

        given("닉네임을 설정하지 않았을 때") {
            val repository = mockk<AdminRepository>()

            `when`("실행하면") {
                then("아무것도 조회하지 않고 false이다") {
                    service("  ", repository).execute() shouldBe false
                    verify(exactly = 0) { repository.existsByStatus(any()) }
                    verify(exactly = 0) { repository.findByNicknameForUpdate(any()) }
                }
            }
        }

        given("승인된 관리자가 이미 있을 때") {
            val repository = mockk<AdminRepository>()
            every { repository.existsByStatus(Status.ACCEPTED) } returns true

            `when`("실행하면") {
                then("후보를 조회하지 않고 false이다") {
                    service("first-admin", repository).execute() shouldBe false
                    verify(exactly = 0) { repository.findByNicknameForUpdate(any()) }
                }
            }
        }

        given("승인된 관리자가 없고 그 닉네임으로 가입한 계정이 없을 때") {
            val repository = mockk<AdminRepository>()
            every { repository.existsByStatus(Status.ACCEPTED) } returns false
            every { repository.findByNicknameForUpdate("first-admin") } returns null

            `when`("실행하면") {
                then("false이다") {
                    service("first-admin", repository).execute() shouldBe false
                }
            }
        }

        given("승인 대기 상태의 계정이 있을 때") {
            val repository = mockk<AdminRepository>()
            val pending = admin()
            every { repository.existsByStatus(Status.ACCEPTED) } returns false
            every { repository.findByNicknameForUpdate("first-admin") } returns pending

            `when`("실행하면") {
                val result = service(" first-admin ", repository).execute()

                then("행을 한 번만 잠그고 관리자 권한과 승인 상태로 바꾼다") {
                    result shouldBe true
                    pending.status shouldBe Status.ACCEPTED
                    pending.authority shouldBe Authority.ROLE_ADMIN
                    verify(exactly = 1) { repository.findByNicknameForUpdate("first-admin") }
                }
            }
        }

        given("이미 승인된 계정일 때") {
            val repository = mockk<AdminRepository>()
            val accepted = admin(Status.ACCEPTED)
            every { repository.existsByStatus(Status.ACCEPTED) } returns false
            every { repository.findByNicknameForUpdate("first-admin") } returns accepted

            `when`("실행하면") {
                then("false이고 권한을 바꾸지 않는다") {
                    service("first-admin", repository).execute() shouldBe false
                    accepted.authority shouldBe Authority.ROLE_STANDARD
                }
            }
        }

        given("행을 잠그는 사이에 다른 인스턴스가 먼저 승인했을 때") {
            val repository = mockk<AdminRepository>()
            val pending = admin()
            // 잠그기 전에는 승인된 관리자가 없었지만 잠근 뒤에 다시 확인하면 있다
            every { repository.existsByStatus(Status.ACCEPTED) } returnsMany listOf(false, true)
            every { repository.findByNicknameForUpdate("first-admin") } returns pending

            `when`("실행하면") {
                then("false이고 승인하지 않는다") {
                    service("first-admin", repository).execute() shouldBe false
                    pending.status shouldBe Status.PENDING
                }
            }
        }
    })
