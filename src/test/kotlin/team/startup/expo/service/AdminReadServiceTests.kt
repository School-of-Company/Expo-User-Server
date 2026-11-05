package team.startup.expo.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import team.startup.expo.domain.user.entity.Status
import team.startup.expo.domain.user.repository.AdminProfileView
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.domain.user.repository.AdminSummaryView
import team.startup.expo.domain.user.service.impl.GetMyInformationServiceImpl
import team.startup.expo.domain.user.service.impl.GetPendingAdminsServiceImpl
import team.startup.expo.global.exception.ExpectedException

class AdminReadServiceTests :
    BehaviorSpec({
        fun profile() =
            object : AdminProfileView {
                override val name = "홍길동"
                override val nickname = "hong"
                override val email = "hong@test.com"
            }

        fun summary(id: Long) =
            object : AdminSummaryView {
                override val id = id
                override val name = "홍길동"
                override val nickname = "hong"
                override val email = "hong@test.com"
                override val phoneNumber = "01012345678"
            }

        given("내 정보를 조회할 때") {
            val adminRepository = mockk<AdminRepository>()
            val service = GetMyInformationServiceImpl(adminRepository)

            `when`("관리자가 있으면") {
                every { adminRepository.findProfileById(1L) } returns profile()

                then("이름, 닉네임, 이메일만 돌려준다") {
                    val result = service.execute(1L)

                    result.name shouldBe "홍길동"
                    result.nickname shouldBe "hong"
                    result.email shouldBe "hong@test.com"
                }
            }
            `when`("관리자가 없으면") {
                every { adminRepository.findProfileById(2L) } returns null

                then("404이다") {
                    shouldThrow<ExpectedException> { service.execute(2L) }.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("승인 대기 목록을 조회할 때") {
            val adminRepository = mockk<AdminRepository>()
            val service = GetPendingAdminsServiceImpl(adminRepository)

            `when`("대기 중인 관리자가 있으면") {
                every { adminRepository.findSummariesByStatus(Status.PENDING) } returns listOf(summary(1L), summary(2L))

                then("PENDING만 조회해 id와 연락처를 담아 돌려준다") {
                    val result = service.execute()

                    result.map { it.id } shouldBe listOf(1L, 2L)
                    result[0].phoneNumber shouldBe "01012345678"
                    verify(exactly = 1) { adminRepository.findSummariesByStatus(Status.PENDING) }
                }
            }
            `when`("대기 중인 관리자가 없으면") {
                every { adminRepository.findSummariesByStatus(Status.PENDING) } returns emptyList()

                then("빈 목록이다") {
                    service.execute() shouldBe emptyList()
                }
            }
        }
    })
