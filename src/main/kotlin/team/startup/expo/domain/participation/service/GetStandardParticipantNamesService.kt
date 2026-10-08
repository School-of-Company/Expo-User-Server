package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.GetStandardParticipantNamesReqDto
import team.startup.expo.domain.participation.presentation.dto.response.StandardParticipantNameResDto

interface GetStandardParticipantNamesService {
    fun execute(reqDto: GetStandardParticipantNamesReqDto): List<StandardParticipantNameResDto>
}
