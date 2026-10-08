package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.GetParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.response.GetParticipantResDto

interface GetParticipantsService {
    fun execute(
        expoId: String,
        reqDto: GetParticipantReqDto,
    ): GetParticipantResDto
}
