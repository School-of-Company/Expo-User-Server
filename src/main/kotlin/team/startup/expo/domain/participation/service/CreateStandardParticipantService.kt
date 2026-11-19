package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.CreateStandardParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.response.CreateStandardParticipantResDto
import team.startup.expo.global.client.expo.ExpoFeatures

interface CreateStandardParticipantService {
    /** [features]는 트랜잭션 밖에서 읽은 기능 설정이다. 새로 저장하기 전에 등록 구분과 동행자 기능을 검사한다. */
    fun execute(
        reqDto: CreateStandardParticipantReqDto,
        features: ExpoFeatures,
    ): CreateStandardParticipantResDto
}
