package team.startup.expo.domain.participation.service.impl

import org.springframework.stereotype.Service
import team.startup.expo.domain.participation.entity.ParticipationType
import team.startup.expo.domain.participation.presentation.dto.request.ResolveParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.request.ResolveStandardParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.response.ResolveStandardParticipantResDto
import team.startup.expo.domain.participation.service.ResolveParticipantService
import team.startup.expo.domain.participation.service.ResolveStandardParticipantService

/** Expo 서비스가 쓰는 일반 참가자 전용 경로. 조회 로직은 [ResolveParticipantService]와 같은 것을 쓴다. */
@Service
class ResolveStandardParticipantServiceImpl(
    private val resolveParticipantService: ResolveParticipantService,
) : ResolveStandardParticipantService {
    override fun execute(reqDto: ResolveStandardParticipantReqDto): ResolveStandardParticipantResDto {
        val resolved =
            resolveParticipantService.execute(
                ResolveParticipantReqDto(
                    expoId = reqDto.expoId,
                    phoneNumber = reqDto.phoneNumber,
                    participationType = ParticipationType.STANDARD,
                ),
            )
        return ResolveStandardParticipantResDto(participantId = resolved.participantId)
    }
}
