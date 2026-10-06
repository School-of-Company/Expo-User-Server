package team.startup.expo.domain.training.service

import team.startup.expo.domain.training.presentation.dto.request.ResolveTraineeReqDto
import team.startup.expo.domain.training.presentation.dto.response.ResolveTraineeResDto

interface ResolveTraineeService {
    fun execute(reqDto: ResolveTraineeReqDto): ResolveTraineeResDto
}
