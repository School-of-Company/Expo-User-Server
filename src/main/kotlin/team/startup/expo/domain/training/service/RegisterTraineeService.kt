package team.startup.expo.domain.training.service

import team.startup.expo.domain.training.presentation.dto.request.CreateTraineeReqDto
import team.startup.expo.domain.training.presentation.dto.response.CreateTraineeResDto

interface RegisterTraineeService {
    fun execute(reqDto: CreateTraineeReqDto): CreateTraineeResDto
}
