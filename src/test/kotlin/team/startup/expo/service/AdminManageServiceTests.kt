package team.startup.expo.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.springframework.http.HttpStatus
import team.startup.expo.domain.auth.service.RefreshTokenService
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.entity.Authority
import team.startup.expo.domain.user.entity.Status
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.domain.user.service.impl.AcceptAdminServiceImpl
import team.startup.expo.domain.user.service.impl.RefuseAdminServiceImpl
import team.startup.expo.domain.user.service.impl.WithdrawalAdminServiceImpl
import team.startup.expo.global.exception.ExpectedException

class AdminManageServiceTests :
    BehaviorSpec({
        fun admin(
            id: Long,
            status: Status = Status.PENDING,
        ) = Admin(
            id = id,
            name = "관리자",
            nickname = "admin$id",
            email = "admin$id@test.com",
            password = "encoded",
            phoneNumber = "0101234000$id",
            status = status,
        )

        given("승인 대기 중인 관리자가 있을 때") {
            val adminRepository = mockk<AdminRepository>()
            val service = AcceptAdminServiceImpl(adminRepository)
            val pending = admin(1L)
            every { adminRepository.findByIdForUpdate(1L) } returns pending

            `when`("승인하면") {
                service.execute(1L)

                then("행을 잠그고 조회해 관리자 권한과 승인 상태가 된다") {
                    pending.status shouldBe Status.ACCEPTED
                    pending.authority shouldBe Authority.ROLE_ADMIN
                    verify(exactly = 1) { adminRepository.findByIdForUpdate(1L) }
                }
            }
        }

        given("없는 관리자일 때") {
            val adminRepository = mockk<AdminRepository>()
            every { adminRepository.findByIdForUpdate(any()) } returns null

            `when`("승인하면") {
                then("404이다") {
                    shouldThrow<ExpectedException> { AcceptAdminServiceImpl(adminRepository).execute(9L) }.status shouldBe
                        HttpStatus.NOT_FOUND
                }
            }
            `when`("거절하면") {
                then("404이고 아무것도 삭제하지 않는다") {
                    shouldThrow<ExpectedException> { RefuseAdminServiceImpl(adminRepository).execute(9L) }.status shouldBe
                        HttpStatus.NOT_FOUND
                    verify(exactly = 0) { adminRepository.delete(any()) }
                }
            }
        }

        given("승인 대기 중인 관리자를 거절할 때") {
            val adminRepository = mockk<AdminRepository>()
            val pending = admin(2L)
            every { adminRepository.findByIdForUpdate(2L) } returns pending
            every { adminRepository.delete(pending) } just runs

            `when`("거절하면") {
                RefuseAdminServiceImpl(adminRepository).execute(2L)

                then("그 관리자를 삭제한다") {
                    verify(exactly = 1) { adminRepository.delete(pending) }
                }
            }
        }

        given("이미 승인된 관리자일 때") {
            val adminRepository = mockk<AdminRepository>()
            val accepted = admin(3L, Status.ACCEPTED)
            every { adminRepository.findByIdForUpdate(3L) } returns accepted

            `when`("거절하면") {
                then("409이고 승인된 계정을 삭제하지 않는다") {
                    val error = shouldThrow<ExpectedException> { RefuseAdminServiceImpl(adminRepository).execute(3L) }

                    error.status shouldBe HttpStatus.CONFLICT
                    error.message shouldBe "이미 수락한 유저입니다."
                    verify(exactly = 0) { adminRepository.delete(any()) }
                }
            }
        }

        given("탈퇴하는 관리자가 있을 때") {
            val adminRepository = mockk<AdminRepository>()
            val refreshTokenService = mockk<RefreshTokenService>()
            val service = WithdrawalAdminServiceImpl(adminRepository, refreshTokenService)
            val existing = admin(4L, Status.ACCEPTED)
            every { adminRepository.findByIdForUpdate(4L) } returns existing
            every { adminRepository.delete(existing) } just runs
            every { refreshTokenService.revoke(any()) } just runs

            `when`("탈퇴하면") {
                service.execute(4L)

                then("관리자를 삭제하고 refresh token도 폐기한다") {
                    verify(exactly = 1) { adminRepository.delete(existing) }
                    verify(exactly = 1) { refreshTokenService.revoke(4L) }
                }
            }
        }

        given("이미 없는 관리자가 탈퇴할 때") {
            val adminRepository = mockk<AdminRepository>()
            val refreshTokenService = mockk<RefreshTokenService>()
            every { adminRepository.findByIdForUpdate(5L) } returns null
            every { refreshTokenService.revoke(any()) } just runs

            `when`("탈퇴하면") {
                WithdrawalAdminServiceImpl(adminRepository, refreshTokenService).execute(5L)

                then("오류 없이 끝나고 남은 refresh token은 폐기한다") {
                    verify(exactly = 0) { adminRepository.delete(any()) }
                    verify(exactly = 1) { refreshTokenService.revoke(5L) }
                }
            }
        }
    })
