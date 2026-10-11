package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.GetManagedParticipantsReqDto
import team.startup.expo.domain.participation.presentation.dto.response.GetManagedParticipantsResDto

interface GetManagedParticipantsService {
    fun execute(reqDto: GetManagedParticipantsReqDto): GetManagedParticipantsResDto
}
