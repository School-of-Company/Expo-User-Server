package team.startup.expo.domain.auth.service

import team.startup.expo.domain.auth.presentation.dto.request.SendParticipantCodeReqDto

interface SendParticipantCodeService {
    fun execute(reqDto: SendParticipantCodeReqDto)
}
