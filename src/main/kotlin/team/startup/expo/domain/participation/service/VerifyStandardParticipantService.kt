package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.VerifyStandardParticipantReqDto

interface VerifyStandardParticipantService {
    fun execute(reqDto: VerifyStandardParticipantReqDto)
}
