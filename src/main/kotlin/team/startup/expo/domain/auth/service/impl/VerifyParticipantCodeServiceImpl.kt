package team.startup.expo.domain.auth.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import team.startup.expo.domain.auth.model.IssuedParticipantSession
import team.startup.expo.domain.auth.presentation.dto.request.VerifyParticipantCodeReqDto
import team.startup.expo.domain.auth.service.ParticipantSessionService
import team.startup.expo.domain.auth.service.VerifyParticipantCodeService
import team.startup.expo.global.client.expo.ExpoPeriodReader
import team.startup.expo.global.client.notification.NotificationAuthSender
import team.startup.expo.global.exception.ExpectedException

/**
 * 인증번호를 Notification에서 검증하고, 성공하면 그 행사에 한정한 세션을 만든다. 클라이언트가 보내는 "검증됨" 값이나
 * 전화번호만으로는 세션이 생기지 않는다.
 *
 * 검증에 성공한 인증번호는 한 번만 쓸 수 있다. Notification은 같은 번호로 다시 검증해도 성공으로 답할 수 있으므로,
 * 성공한 `(번호, 코드)`의 사용 기록과 세션 저장을 하나의 원자적 Redis 작업으로 묶어, 이미 쓴 코드면 세션을 만들지 않는다.
 * 같은 코드를 동시에 보내도 하나만 통과하고, 사용 기록만 남고 세션이 없는 상태는 생기지 않는다. 다만 Redis가 저장을 끝낸
 * 뒤 응답이 유실되면 사용자는 쿠키를 받지 못하므로 새 인증번호를 받아야 한다(기록을 지우면 중복 발급될 수 있어 지우지 않는다).
 */
@Service
class VerifyParticipantCodeServiceImpl(
    private val expoPeriodReader: ExpoPeriodReader,
    private val notificationAuthSender: NotificationAuthSender,
    private val participantSessionService: ParticipantSessionService,
) : VerifyParticipantCodeService {
    override fun execute(reqDto: VerifyParticipantCodeReqDto): IssuedParticipantSession {
        expoPeriodReader.find(reqDto.expoId) ?: throw ExpectedException(HttpStatus.NOT_FOUND, "박람회를 찾지 못했습니다.")
        notificationAuthSender.verifyCode(reqDto.phoneNumber, reqDto.code)
        return participantSessionService.issueIfCodeUnused(reqDto.expoId, reqDto.phoneNumber, reqDto.code)
            ?: throw ExpectedException(HttpStatus.CONFLICT, "이미 사용한 인증번호입니다.")
    }
}
