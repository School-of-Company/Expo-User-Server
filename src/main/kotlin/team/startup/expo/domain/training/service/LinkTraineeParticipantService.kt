package team.startup.expo.domain.training.service

import team.startup.expo.domain.training.presentation.dto.request.TraineeParticipantLinkReqDto
import team.startup.expo.domain.training.presentation.dto.response.TraineeParticipantLinkResDto

interface LinkTraineeParticipantService {
    fun link(reqDto: TraineeParticipantLinkReqDto): TraineeParticipantLinkResDto

    fun revoke(reqDto: TraineeParticipantLinkReqDto): TraineeParticipantLinkResDto
}
