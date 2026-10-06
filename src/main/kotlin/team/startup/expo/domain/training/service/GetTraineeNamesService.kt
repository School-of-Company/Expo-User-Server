package team.startup.expo.domain.training.service

import team.startup.expo.domain.training.presentation.dto.request.GetTraineeNamesReqDto
import team.startup.expo.domain.training.presentation.dto.response.TraineeNameResDto

interface GetTraineeNamesService {
    fun execute(reqDto: GetTraineeNamesReqDto): List<TraineeNameResDto>
}
