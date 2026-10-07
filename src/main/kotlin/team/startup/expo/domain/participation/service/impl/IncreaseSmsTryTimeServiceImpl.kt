package team.startup.expo.domain.participation.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.entity.ParticipationType
import team.startup.expo.domain.participation.presentation.dto.request.IncreaseSmsTryTimeReqDto
import team.startup.expo.domain.participation.repository.SmsTryEventRepository
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.IncreaseSmsTryTimeService
import team.startup.expo.domain.participation.service.SmsTryCounter
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.util.PhoneNumbers

/**
 * QR 문자를 보낸 뒤 문자 서비스가 부른다. 발송 완료 이벤트 소비(`RecordQrSmsSentService`)로 대체될 예정이며, 전환이 끝나면 제거한다.
 *
 *  v1은 일반 참가자의 발송 횟수만 세므로 연수자는 받지 않는다.
 * 횟수는 DB에서 한 번에 올려 동시 호출이 서로의 증가를 덮어쓰지 않게 한다.
 *
 * 같은 `eventId`는 한 번만 올린다. 문자 서비스가 발송 뒤 호출을 재시도해도 횟수가 중복으로 올라가지 않아야 하기
 * 때문이다(횟수가 2에 닿으면 재신청이 막힌다). 이미 처리한 `eventId`는 아무것도 하지 않고 성공하며, 다른 참가자에
 * 쓴 `eventId`는 호출자의 실수이므로 409다. `eventId`가 없는 호출은 문자 서비스가 보내기 시작하기 전의 전환 기간만 받으며, 기존처럼 호출마다 올린다.
 */
@Service
class IncreaseSmsTryTimeServiceImpl(
    private val standardParticipantRepository: StandardParticipantRepository,
    private val smsTryCounter: SmsTryCounter,
) : IncreaseSmsTryTimeService {
    @Transactional
    override fun execute(reqDto: IncreaseSmsTryTimeReqDto) {
        if (reqDto.participationType != ParticipationType.STANDARD) {
            throw ExpectedException(HttpStatus.BAD_REQUEST, "일반 참가자만 문자 발송 횟수를 기록합니다.")
        }
        val digits = PhoneNumbers.digitsOnly(reqDto.phoneNumber)
        val participantId =
            standardParticipantRepository.findByExpoIdAndPhoneNumber(reqDto.expoId, reqDto.phoneNumber)?.id
                ?: PhoneNumbers
                    .select(standardParticipantRepository.findAllByExpoIdAndDigits(reqDto.expoId, digits), reqDto.phoneNumber) {
                        it.phoneNumber
                    }?.id
                ?: throw ExpectedException(HttpStatus.NOT_FOUND, "행사 참가자를 찾지 못 했습니다.")
        val eventId = reqDto.eventId
        if (eventId != null) {
            if (smsTryCounter.increaseOnce(eventId, participantId) == SmsTryCounter.Outcome.CONFLICT) {
                throw ExpectedException(HttpStatus.CONFLICT, "이미 다른 참가자에 쓴 eventId입니다.")
            }
            return
        }
        standardParticipantRepository.increaseSmsTryTime(participantId)
    }
}
