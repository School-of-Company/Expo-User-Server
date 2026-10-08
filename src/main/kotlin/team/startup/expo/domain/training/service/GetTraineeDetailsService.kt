package team.startup.expo.domain.training.service

import team.startup.expo.domain.training.presentation.dto.response.TraineeDetailItemResDto
import team.startup.expo.domain.training.presentation.dto.response.TraineeDetailResDto
import team.startup.expo.global.dto.DetailPageReqDto
import team.startup.expo.global.dto.DetailPageResDto

interface GetTraineeDetailsService {
    fun page(
        expoId: String,
        reqDto: DetailPageReqDto,
    ): DetailPageResDto<TraineeDetailItemResDto>

    fun one(traineeId: Long): TraineeDetailResDto
}
