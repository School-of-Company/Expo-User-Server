package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.GetParticipantQrReqDto
import team.startup.expo.domain.participation.presentation.dto.response.ParticipantQrResDto

interface GetParticipantQrService {
    fun execute(reqDto: GetParticipantQrReqDto): ParticipantQrResDto
}
