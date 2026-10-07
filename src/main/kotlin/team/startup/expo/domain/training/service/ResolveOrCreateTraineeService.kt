package team.startup.expo.domain.training.service

import team.startup.expo.domain.training.presentation.dto.request.ResolveOrCreateTraineeReqDto
import team.startup.expo.domain.training.presentation.dto.response.ResolveOrCreateTraineeResDto

interface ResolveOrCreateTraineeService {
    fun execute(reqDto: ResolveOrCreateTraineeReqDto): ResolveOrCreateTraineeResDto
}
