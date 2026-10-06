package team.startup.expo.domain.participation.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.entity.ParticipationType
import team.startup.expo.domain.participation.presentation.dto.request.IncreaseSmsTryTimeReqDto
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.IncreaseSmsTryTimeService
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.util.PhoneNumbers

/**
 * QR 문자를 보낸 뒤 문자 서비스가 부른다. v1은 일반 참가자의 발송 횟수만 세므로 연수자는 받지 않는다.
 * 횟수는 DB에서 한 번에 올려 동시 호출이 서로의 증가를 덮어쓰지 않게 한다.
 */
@Service
class IncreaseSmsTryTimeServiceImpl(
    private val standardParticipantRepository: StandardParticipantRepository,
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
        standardParticipantRepository.increaseSmsTryTime(participantId)
    }
}
