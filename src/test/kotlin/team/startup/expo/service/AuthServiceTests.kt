package team.startup.expo.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import team.startup.expo.domain.auth.presentation.dto.request.SignInReqDto
import team.startup.expo.domain.auth.presentation.dto.request.SignUpReqDto
import team.startup.expo.domain.auth.presentation.dto.response.TokenResDto
import team.startup.expo.domain.auth.service.RefreshTokenService
import team.startup.expo.domain.auth.service.RotatedRefreshToken
import team.startup.expo.domain.auth.service.impl.LogoutServiceImpl
import team.startup.expo.domain.auth.service.impl.ReissueTokenServiceImpl
import team.startup.expo.domain.auth.service.impl.SignInServiceImpl
import team.startup.expo.domain.auth.service.impl.SignUpServiceImpl
import team.startup.expo.domain.auth.service.impl.TokenIssuer
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.entity.Status
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.security.jwt.IssuedToken
import java.time.LocalDateTime
import java.util.Optional

class AuthServiceTests :
    BehaviorSpec({
        fun admin(status: Status) =
            Admin(
                id = 1L,
                name = "홍길동",
                nickname = "hong",
                email = "hong@test.com",
                password = "encoded-password",
                phoneNumber = "01012345678",
                status = status,
            )

        val tokens =
            TokenResDto(
                accessToken = "access",
                refreshToken = "refresh",
                accessTokenExpiresIn = LocalDateTime.of(2026, 10, 1, 10, 0),
                refreshTokenExpiresIn = LocalDateTime.of(2026, 10, 8, 10, 0),
            )

        given("로그인할 때") {
            val adminRepository = mockk<AdminRepository>()
            val passwordEncoder = mockk<PasswordEncoder>()
            val tokenIssuer = mockk<TokenIssuer>()
            val service = SignInServiceImpl(adminRepository, passwordEncoder, tokenIssuer)
            val request = SignInReqDto("hong", "plain-password")

            `when`("닉네임이 없으면") {
                every { adminRepository.findByNickname("hong") } returns null

                then("404이고 비밀번호를 비교하지 않는다") {
                    shouldThrow<ExpectedException> { service.execute(request) }.status shouldBe HttpStatus.NOT_FOUND
                    verify(exactly = 0) { passwordEncoder.matches(any(), any()) }
                }
            }
            `when`("비밀번호가 다르면") {
                every { adminRepository.findByNickname("hong") } returns admin(Status.ACCEPTED)
                every { passwordEncoder.matches("plain-password", "encoded-password") } returns false

                then("400이고 토큰을 발급하지 않는다") {
                    shouldThrow<ExpectedException> { service.execute(request) }.status shouldBe HttpStatus.BAD_REQUEST
                    verify(exactly = 0) { tokenIssuer.issue(any()) }
                }
            }
            `when`("비밀번호가 맞아도 아직 승인되지 않았으면") {
                every { adminRepository.findByNickname("hong") } returns admin(Status.PENDING)
                every { passwordEncoder.matches("plain-password", "encoded-password") } returns true

                then("403이고 토큰을 발급하지 않는다") {
                    shouldThrow<ExpectedException> { service.execute(request) }.status shouldBe HttpStatus.FORBIDDEN
                    verify(exactly = 0) { tokenIssuer.issue(any()) }
                }
            }
            `when`("승인된 관리자의 비밀번호가 맞으면") {
                val accepted = admin(Status.ACCEPTED)
                every { adminRepository.findByNickname("hong") } returns accepted
                every { passwordEncoder.matches("plain-password", "encoded-password") } returns true
                every { tokenIssuer.issue(accepted) } returns tokens

                then("토큰을 발급한다") {
                    service.execute(request) shouldBe tokens
                }
            }
        }

        given("회원가입할 때") {
            val adminRepository = mockk<AdminRepository>()
            val passwordEncoder = mockk<PasswordEncoder>()
            val service = SignUpServiceImpl(adminRepository, passwordEncoder)
            val request = SignUpReqDto("홍길동", "hong", "hong@test.com", "plain-password", "01012345678")

            fun nothingTaken() {
                every { adminRepository.existsByPhoneNumber(any()) } returns false
                every { adminRepository.existsByEmail(any()) } returns false
                every { adminRepository.existsByNickname(any()) } returns false
                every { passwordEncoder.encode("plain-password") } returns "encoded-password"
            }

            `when`("전화번호가 이미 있으면") {
                nothingTaken()
                every { adminRepository.existsByPhoneNumber("01012345678") } returns true

                then("409이고 저장하지 않는다") {
                    val error = shouldThrow<ExpectedException> { service.execute(request) }

                    error.status shouldBe HttpStatus.CONFLICT
                    error.message shouldBe "이미 존재하는 전화번호입니다."
                    verify(exactly = 0) { adminRepository.saveAndFlush(any<Admin>()) }
                }
            }
            `when`("이메일이 이미 있으면") {
                nothingTaken()
                every { adminRepository.existsByEmail("hong@test.com") } returns true

                then("이메일 중복으로 409이다") {
                    shouldThrow<ExpectedException> { service.execute(request) }.message shouldBe "이미 존재하는 이메일입니다."
                }
            }
            `when`("닉네임이 이미 있으면") {
                nothingTaken()
                every { adminRepository.existsByNickname("hong") } returns true

                then("닉네임 중복으로 409이다") {
                    shouldThrow<ExpectedException> { service.execute(request) }.message shouldBe "이미 존재하는 닉네임입니다."
                }
            }
            `when`("확인과 저장 사이에 같은 값이 들어와 UNIQUE 제약에 걸리면") {
                nothingTaken()
                every { adminRepository.saveAndFlush(any<Admin>()) } throws DataIntegrityViolationException("duplicate")

                then("500이 아니라 409이다") {
                    val error = shouldThrow<ExpectedException> { service.execute(request) }

                    error.status shouldBe HttpStatus.CONFLICT
                    error.message shouldBe "이미 존재하는 회원 정보입니다."
                }
            }
            `when`("중복이 없으면") {
                nothingTaken()
                val saved = slot<Admin>()
                every { adminRepository.saveAndFlush(capture(saved)) } answers { saved.captured }

                service.execute(request)

                then("암호화한 비밀번호로 승인 대기 일반 관리자를 저장한다") {
                    saved.captured.password shouldBe "encoded-password"
                    saved.captured.status shouldBe Status.PENDING
                    saved.captured.nickname shouldBe "hong"
                }
            }
        }

        given("토큰을 재발급할 때") {
            val adminRepository = mockk<AdminRepository>()
            val refreshTokenService = mockk<RefreshTokenService>()
            val tokenIssuer = mockk<TokenIssuer>()
            val service = ReissueTokenServiceImpl(adminRepository, refreshTokenService, tokenIssuer)

            `when`("Bearer 접두사가 없으면") {
                then("401이고 토큰 저장소를 부르지 않는다") {
                    shouldThrow<ExpectedException> { service.execute("raw-token") }.status shouldBe HttpStatus.UNAUTHORIZED
                    verify(exactly = 0) { refreshTokenService.rotate(any()) }
                }
            }
            `when`("교체할 수 없는 토큰이면") {
                every { refreshTokenService.rotate("old") } returns null

                then("401이다") {
                    shouldThrow<ExpectedException> { service.execute("Bearer old") }.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }
            `when`("탈퇴한 관리자의 토큰이면") {
                val issued = IssuedToken("new-refresh", LocalDateTime.of(2026, 10, 8, 10, 0))
                every { refreshTokenService.rotate("old") } returns RotatedRefreshToken(7L, issued)
                every { adminRepository.findById(7L) } returns Optional.empty()
                every { refreshTokenService.revoke(7L) } just runs

                then("401이고 남은 refresh token을 정리한다") {
                    shouldThrow<ExpectedException> { service.execute("Bearer old") }.status shouldBe HttpStatus.UNAUTHORIZED
                    verify(exactly = 1) { refreshTokenService.revoke(7L) }
                }
            }
            `when`("유효한 토큰이면") {
                val issued = IssuedToken("new-refresh", LocalDateTime.of(2026, 10, 8, 10, 0))
                val accepted = admin(Status.ACCEPTED)
                every { refreshTokenService.rotate("old") } returns RotatedRefreshToken(1L, issued)
                every { adminRepository.findById(1L) } returns Optional.of(accepted)
                every { tokenIssuer.withRefreshToken(accepted, issued) } returns tokens

                then("교체된 refresh token으로 새 토큰 쌍을 돌려준다") {
                    service.execute("Bearer  old ") shouldBe tokens
                }
            }
        }

        given("로그아웃할 때") {
            val refreshTokenService = mockk<RefreshTokenService>()
            every { refreshTokenService.revoke(any()) } just runs

            `when`("관리자 id로 로그아웃하면") {
                LogoutServiceImpl(refreshTokenService).execute(3L)

                then("그 관리자의 refresh token을 폐기한다") {
                    verify(exactly = 1) { refreshTokenService.revoke(3L) }
                }
            }
        }
    })
