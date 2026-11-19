package team.startup.expo.domain.training.service

import team.startup.expo.domain.training.presentation.dto.request.ResolveOrCreateTraineeReqDto
import team.startup.expo.domain.training.presentation.dto.response.ResolveOrCreateTraineeResDto
import team.startup.expo.global.client.expo.ExpoFeatures

interface ResolveOrCreateTraineeService {
    /** [features]는 트랜잭션 밖에서 읽은 기능 설정이다. 이미 있는 연수자를 돌려줄 때는 검사하지 않는다. */
    fun execute(
        reqDto: ResolveOrCreateTraineeReqDto,
        features: ExpoFeatures,
    ): ResolveOrCreateTraineeResDto
}
