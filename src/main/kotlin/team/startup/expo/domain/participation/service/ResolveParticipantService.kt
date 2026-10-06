package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.ResolveParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.response.ResolveParticipantResDto

interface ResolveParticipantService {
    fun execute(reqDto: ResolveParticipantReqDto): ResolveParticipantResDto
}
