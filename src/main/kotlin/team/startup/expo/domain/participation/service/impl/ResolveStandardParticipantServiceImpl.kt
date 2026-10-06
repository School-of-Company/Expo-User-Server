package team.startup.expo.domain.participation.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.presentation.dto.request.ResolveStandardParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.response.ResolveStandardParticipantResDto
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.ResolveStandardParticipantService
import team.startup.expo.global.exception.ExpectedException

@Service
class ResolveStandardParticipantServiceImpl(
    private val standardParticipantRepository: StandardParticipantRepository,
) : ResolveStandardParticipantService {
    @Transactional(readOnly = true)
    override fun execute(reqDto: ResolveStandardParticipantReqDto): ResolveStandardParticipantResDto {
        val participant =
            standardParticipantRepository.findByExpoIdAndPhoneNumber(reqDto.expoId, reqDto.phoneNumber)
                ?: throw ExpectedException(HttpStatus.NOT_FOUND, "행사 참가자를 찾지 못 했습니다.")
        return ResolveStandardParticipantResDto(participantId = requireNotNull(participant.id))
    }
}
