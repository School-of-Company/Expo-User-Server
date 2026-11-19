package team.startup.expo.domain.training.service

import team.startup.expo.domain.training.presentation.dto.request.CreateTraineeReqDto
import team.startup.expo.domain.training.presentation.dto.response.CreateTraineeResDto
import team.startup.expo.global.client.expo.ExpoFeatures

interface RegisterTraineeService {
    /** [features]는 트랜잭션 밖에서 읽은 기능 설정이다. 같은 `requestId`의 재시도는 검사하지 않고 처음 결과를 돌려준다. */
    fun execute(
        reqDto: CreateTraineeReqDto,
        features: ExpoFeatures,
    ): CreateTraineeResDto
}
