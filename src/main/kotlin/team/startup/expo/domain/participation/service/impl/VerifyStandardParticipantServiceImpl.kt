package team.startup.expo.domain.participation.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.presentation.dto.request.VerifyStandardParticipantReqDto
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.VerifyStandardParticipantService
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.util.ParticipantCode

/**
 * 참여 서비스가 입장·출석을 기록하기 전에 QR의 `participantId`와 `code`가 맞는지만 확인한다. 아무것도 기록하지 않는다.
 *
 * 참가자가 없거나, 다른 박람회의 참가자이거나, `code`가 다르면 입장 기록(`POST /internal/entries`)과 같은 404로 답해
 * 남의 참가자 ID가 있는지 드러나지 않게 한다.
 */
@Service
class VerifyStandardParticipantServiceImpl(
    private val standardParticipantRepository: StandardParticipantRepository,
) : VerifyStandardParticipantService {
    @Transactional(readOnly = true)
    override fun execute(reqDto: VerifyStandardParticipantReqDto) {
        val participant = standardParticipantRepository.findByIdAndExpoId(reqDto.participantId!!, reqDto.expoId)
        if (participant == null || !ParticipantCode.matches(participant.code, reqDto.code)) {
            throw ExpectedException(HttpStatus.NOT_FOUND, "행사 참가자를 찾지 못 했습니다.")
        }
    }
}
