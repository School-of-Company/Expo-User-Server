package team.startup.expo.domain.auth.service.impl

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import team.startup.expo.domain.auth.presentation.dto.request.SignUpReqDto
import team.startup.expo.domain.auth.service.SignUpService
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.global.client.notification.NotificationAuthSender
import team.startup.expo.global.exception.ExpectedException

/**
 * 가입 요청의 인증번호를 Notification에 다시 확인해 전화 인증을 거친 번호만 가입시킨다. 관리자 전화번호는 유니크라 같은
 * 인증번호로 두 번 가입할 수 없으므로 별도의 사용 기록은 두지 않는다. 외부 호출이 DB 연결을 잡고 있지 않도록 이 메서드는
 * 트랜잭션 없이 실행하고, 저장 한 번은 `saveAndFlush`의 트랜잭션으로 충분하다.
 */
@Service
class SignUpServiceImpl(
    private val adminRepository: AdminRepository,
    private val passwordEncoder: PasswordEncoder,
    private val notificationAuthSender: NotificationAuthSender,
) : SignUpService {
    override fun execute(reqDto: SignUpReqDto) {
        if (adminRepository.existsByPhoneNumber(reqDto.phoneNumber)) {
            throw ExpectedException(HttpStatus.CONFLICT, "이미 존재하는 전화번호입니다.")
        }
        if (adminRepository.existsByEmail(reqDto.email)) {
            throw ExpectedException(HttpStatus.CONFLICT, "이미 존재하는 이메일입니다.")
        }
        if (adminRepository.existsByNickname(reqDto.nickname)) {
            throw ExpectedException(HttpStatus.CONFLICT, "이미 존재하는 닉네임입니다.")
        }

        notificationAuthSender.verifyCode(reqDto.phoneNumber, reqDto.code)

        try {
            // 확인과 저장 사이에 같은 값이 들어오면 UNIQUE 제약이 막는다. 커밋 시점이 아니라 여기서 409로 바꾸기 위해 즉시 반영한다.
            adminRepository.saveAndFlush(reqDto.toEntity(requireNotNull(passwordEncoder.encode(reqDto.password))))
        } catch (e: DataIntegrityViolationException) {
            throw ExpectedException(HttpStatus.CONFLICT, "이미 존재하는 회원 정보입니다.")
        }
    }

    private fun SignUpReqDto.toEntity(encodedPassword: String) =
        Admin(
            name = name,
            nickname = nickname,
            email = email,
            password = encodedPassword,
            phoneNumber = phoneNumber,
        )
}
