package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.CreateStandardParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.response.CreateStandardParticipantResDto

interface CreateStandardParticipantService {
    fun execute(reqDto: CreateStandardParticipantReqDto): CreateStandardParticipantResDto
}
