package team.startup.expo.domain.auth.service.impl

import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.auth.presentation.dto.request.SignInReqDto
import team.startup.expo.domain.auth.presentation.dto.response.TokenResDto
import team.startup.expo.domain.auth.service.SignInAttemptLimiter
import team.startup.expo.domain.auth.service.SignInService
import team.startup.expo.domain.user.entity.Status
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.global.exception.ExpectedException
import java.util.UUID

@Service
class SignInServiceImpl(
    private val adminRepository: AdminRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenIssuer: TokenIssuer,
    private val signInAttemptLimiter: SignInAttemptLimiter,
) : SignInService {
    // 닉네임이 없을 때도 같은 비용으로 비밀번호를 비교하려고 쓰는 해시다. 어떤 비밀번호와도 맞지 않는다
    private val unknownNicknameHash by lazy { requireNotNull(passwordEncoder.encode(UUID.randomUUID().toString())) }

    @Transactional(readOnly = true)
    override fun execute(reqDto: SignInReqDto): TokenResDto {
        signInAttemptLimiter.requireNotLocked(reqDto.nickname)

        val admin = adminRepository.findByNickname(reqDto.nickname)
        // 없는 닉네임과 틀린 비밀번호를 같은 응답과 같은 비용으로 처리해, 어떤 닉네임이 관리자인지 응답이나 시간으로 알 수 없게 한다
        val passwordMatches = passwordEncoder.matches(reqDto.password, admin?.password ?: unknownNicknameHash)
        if (admin == null || !passwordMatches) {
            signInAttemptLimiter.recordFailure(reqDto.nickname)
            throw ExpectedException(HttpStatus.BAD_REQUEST, "닉네임 또는 비밀번호가 일치하지 않습니다.")
        }
        if (admin.status != Status.ACCEPTED) {
            throw ExpectedException(HttpStatus.FORBIDDEN, "아직 관리자가 보류 중입니다.")
        }

        signInAttemptLimiter.clear(reqDto.nickname)
        return tokenIssuer.issue(admin)
    }
}
