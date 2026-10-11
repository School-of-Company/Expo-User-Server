package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.SelectManagedParticipantsReqDto
import team.startup.expo.domain.participation.presentation.dto.response.ManagedParticipantResDto

interface SelectManagedParticipantsService {
    fun execute(reqDto: SelectManagedParticipantsReqDto): List<ManagedParticipantResDto>
}
