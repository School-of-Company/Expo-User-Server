package team.startup.expo.domain.training.service

import team.startup.expo.domain.training.presentation.dto.request.ResolveOrCreateTraineeByParticipantReqDto
import team.startup.expo.domain.training.presentation.dto.response.ResolveOrCreateTraineeByParticipantResDto

interface ResolveOrCreateTraineeByParticipantService {
    fun execute(reqDto: ResolveOrCreateTraineeByParticipantReqDto): ResolveOrCreateTraineeByParticipantResDto
}
