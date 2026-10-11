package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.ResolveTraineeByParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.response.ResolveTraineeByParticipantResDto

interface ResolveTraineeByParticipantService {
    fun execute(reqDto: ResolveTraineeByParticipantReqDto): ResolveTraineeByParticipantResDto
}
