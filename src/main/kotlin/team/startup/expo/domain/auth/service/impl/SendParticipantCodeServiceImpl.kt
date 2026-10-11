package team.startup.expo.domain.auth.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import team.startup.expo.domain.auth.presentation.dto.request.SendParticipantCodeReqDto
import team.startup.expo.domain.auth.service.SendParticipantCodeService
import team.startup.expo.global.client.expo.ExpoPeriodReader
import team.startup.expo.global.client.notification.NotificationAuthSender
import team.startup.expo.global.exception.ExpectedException

/**
 * 행사별 인증번호를 요청한다. 없는 박람회로 문자를 보내지 않도록 먼저 박람회가 있는지 확인하고, 발송과 횟수 제한은
 * Notification이 한다. 등록(참가자 생성) 전에도 보낼 수 있다.
 */
@Service
class SendParticipantCodeServiceImpl(
    private val expoPeriodReader: ExpoPeriodReader,
    private val notificationAuthSender: NotificationAuthSender,
) : SendParticipantCodeService {
    override fun execute(reqDto: SendParticipantCodeReqDto) {
        expoPeriodReader.find(reqDto.expoId) ?: throw ExpectedException(HttpStatus.NOT_FOUND, "박람회를 찾지 못했습니다.")
        notificationAuthSender.sendCode(reqDto.phoneNumber)
    }
}
