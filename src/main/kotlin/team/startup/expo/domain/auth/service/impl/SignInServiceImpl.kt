package team.startup.expo.domain.auth.service.impl

import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.auth.presentation.dto.request.SignInReqDto
import team.startup.expo.domain.auth.presentation.dto.response.TokenResDto
import team.startup.expo.domain.auth.service.SignInService
import team.startup.expo.domain.user.entity.Status
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.global.exception.ExpectedException

@Service
class SignInServiceImpl(
    private val adminRepository: AdminRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenIssuer: TokenIssuer,
) : SignInService {
    @Transactional(readOnly = true)
    override fun execute(reqDto: SignInReqDto): TokenResDto {
        val admin =
            adminRepository.findByNickname(reqDto.nickname)
                ?: throw ExpectedException(HttpStatus.NOT_FOUND, "해당 유저를 찾을 수 없습니다.")

        if (!passwordEncoder.matches(reqDto.password, admin.password)) {
            throw ExpectedException(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다.")
        }
        if (admin.status != Status.ACCEPTED) {
            throw ExpectedException(HttpStatus.FORBIDDEN, "아직 관리자가 보류 중입니다.")
        }

        return tokenIssuer.issue(admin)
    }
}
