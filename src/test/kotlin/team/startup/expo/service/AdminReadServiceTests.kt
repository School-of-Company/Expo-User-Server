package team.startup.expo.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpStatus
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.entity.Status
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.domain.user.service.impl.GetMyInformationServiceImpl
import team.startup.expo.domain.user.service.impl.GetPendingAdminsServiceImpl
import team.startup.expo.global.exception.ExpectedException
import java.util.Optional

class AdminReadServiceTests :
    BehaviorSpec({
        fun admin(id: Long?) =
            Admin(
                id = id,
                name = "홍길동",
                nickname = "hong",
                email = "hong@test.com",
                password = "encoded",
                phoneNumber = "01012345678",
            )

        given("내 정보를 조회할 때") {
            val adminRepository = mockk<AdminRepository>()
            val service = GetMyInformationServiceImpl(adminRepository)

            `when`("관리자가 있으면") {
                every { adminRepository.findById(1L) } returns Optional.of(admin(1L))

                then("이름, 닉네임, 이메일만 돌려주고 비밀번호와 전화번호는 싣지 않는다") {
                    val result = service.execute(1L)

                    result.name shouldBe "홍길동"
                    result.nickname shouldBe "hong"
                    result.email shouldBe "hong@test.com"
                    result.toString().contains("encoded") shouldBe false
                }
            }
            `when`("관리자가 없으면") {
                every { adminRepository.findById(2L) } returns Optional.empty()

                then("404이다") {
                    shouldThrow<ExpectedException> { service.execute(2L) }.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("승인 대기 목록을 조회할 때") {
            val adminRepository = mockk<AdminRepository>()
            val service = GetPendingAdminsServiceImpl(adminRepository)

            `when`("대기 중인 관리자가 있으면") {
                every { adminRepository.findByStatus(Status.PENDING) } returns listOf(admin(1L), admin(2L))

                then("PENDING만 조회해 id와 연락처를 담아 돌려준다") {
                    val result = service.execute()

                    result.map { it.id } shouldBe listOf(1L, 2L)
                    result[0].phoneNumber shouldBe "01012345678"
                    result.none { it.toString().contains("encoded") } shouldBe true
                }
            }
            `when`("대기 중인 관리자가 없으면") {
                every { adminRepository.findByStatus(Status.PENDING) } returns emptyList()

                then("빈 목록이다") {
                    service.execute() shouldBe emptyList()
                }
            }
        }
    })
