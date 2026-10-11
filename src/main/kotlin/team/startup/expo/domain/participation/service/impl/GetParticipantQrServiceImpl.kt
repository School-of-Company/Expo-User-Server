package team.startup.expo.domain.participation.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.presentation.dto.request.GetParticipantQrReqDto
import team.startup.expo.domain.participation.presentation.dto.response.ParticipantQrResDto
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.GetParticipantQrService
import team.startup.expo.global.exception.ExpectedException

/**
 * 참여 서비스가 QR 공급 서비스로서 신청 서비스에 QR 값(`participantId`, `code`)을 돌려주려고 부르는 조회다. 참가자가 그 박람회에서
 * 요청한 대표자의 일행(대표자 본인 포함)일 때만 `code`를 내준다. 아무것도 기록하지 않는다.
 *
 * 없는 참가자, 다른 박람회의 참가자, 다른 대표자의 일행, 대표자가 아닌 사람을 대표자로 넘긴 경우를 구분하지 않고 같은 404로 답해
 * 남의 참가자 ID가 있는지, 누구의 일행인지 드러나지 않게 한다. 확정 여부 같은 신청 상태는 호출하는 쪽의 책임이다.
 * `code`는 응답 본문 외에 로그나 오류 메시지에 남기지 않는다.
 */
@Service
class GetParticipantQrServiceImpl(
    private val standardParticipantRepository: StandardParticipantRepository,
) : GetParticipantQrService {
    @Transactional(readOnly = true)
    override fun execute(reqDto: GetParticipantQrReqDto): ParticipantQrResDto {
        val participantId = requireNotNull(reqDto.participantId)
        val representativeId = requireNotNull(reqDto.representativeId)

        val participant = standardParticipantRepository.findByIdAndExpoId(participantId, reqDto.expoId) ?: throw notFound()
        // 대표자 본인(representativeId가 비어 있음)이면 자기가 소유자이고, 동행자면 그 동행자가 딸린 대표자가 소유자다
        val ownerId = participant.representativeId ?: participantId
        if (ownerId != representativeId) throw notFound()
        // 소유자는 같은 박람회의 대표자여야 한다(대표자가 아닌 사람을 대표자로 넘기면 거부)
        val owner = standardParticipantRepository.findByIdAndExpoId(ownerId, reqDto.expoId) ?: throw notFound()
        if (owner.representativeId != null) throw notFound()
        return ParticipantQrResDto(participantId, participant.code)
    }

    private fun notFound() = ExpectedException(HttpStatus.NOT_FOUND, "행사 참가자를 찾지 못 했습니다.")
}
