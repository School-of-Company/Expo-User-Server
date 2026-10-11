package team.startup.expo.domain.auth.service

import team.startup.expo.domain.auth.model.IssuedParticipantSession
import team.startup.expo.domain.auth.presentation.dto.request.VerifyParticipantCodeReqDto

interface VerifyParticipantCodeService {
    fun execute(reqDto: VerifyParticipantCodeReqDto): IssuedParticipantSession
}
