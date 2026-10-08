package team.startup.expo.domain.training.service

import team.startup.expo.domain.training.presentation.dto.request.GetTraineeReqDto
import team.startup.expo.domain.training.presentation.dto.response.GetTraineeResDto

interface GetTraineesService {
    fun execute(
        expoId: String,
        reqDto: GetTraineeReqDto,
    ): List<GetTraineeResDto>
}
