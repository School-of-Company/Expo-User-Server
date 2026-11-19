package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.CreateFieldVisitParticipantsReqDto
import team.startup.expo.domain.participation.presentation.dto.request.ProjectFieldVisitReqDto
import team.startup.expo.domain.participation.presentation.dto.response.FieldVisitParticipantResDto
import team.startup.expo.global.client.expo.ExpoFeatures
import java.util.UUID

interface FieldVisitService {
    /** [features]는 트랜잭션 밖에서 읽은 기능 설정이다. 같은 `requestId`의 재시도는 검사하지 않고 처음 결과를 돌려준다. */
    fun create(
        expoId: String,
        body: CreateFieldVisitParticipantsReqDto,
        features: ExpoFeatures,
    ): List<FieldVisitParticipantResDto>

    fun project(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        body: ProjectFieldVisitReqDto,
    )
}
