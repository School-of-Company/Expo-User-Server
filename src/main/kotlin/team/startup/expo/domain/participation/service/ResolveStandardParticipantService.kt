package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.ResolveStandardParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.response.ResolveStandardParticipantResDto

interface ResolveStandardParticipantService {
    fun execute(reqDto: ResolveStandardParticipantReqDto): ResolveStandardParticipantResDto
}
