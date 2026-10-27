package team.startup.expo.domain.participation.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.presentation.dto.request.GetEntryCardsReqDto
import team.startup.expo.domain.participation.presentation.dto.response.EntryCardsResDto
import team.startup.expo.domain.participation.presentation.dto.response.StandardParticipantEntryCardResDto
import team.startup.expo.domain.participation.presentation.dto.response.TraineeEntryCardResDto
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.GetEntryCardsService
import team.startup.expo.domain.training.repository.TraineeRepository

/**
 * 참여 서비스의 입장 목록(Expo-Attendance-Server#237)을 채우는 읽기 전용 조회다. 값은 `POST /internal/entries` 응답과 같은 규칙이다.
 *
 * `/names`와 달리 없는 id가 있어도 404를 내지 않고 그 id만 뺀다. 입장 뒤에 지워진 참가자 한 명 때문에 그날 목록 전체가 비지 않게 하려는 것이다.
 * 다른 박람회의 id도 같은 방식으로 빠지므로 다른 박람회의 참가자가 있는지는 드러나지 않는다. 요청 순서대로, 중복 id는 한 번만 돌려준다.
 */
@Service
class GetEntryCardsServiceImpl(
    private val standardParticipantRepository: StandardParticipantRepository,
    private val traineeRepository: TraineeRepository,
) : GetEntryCardsService {
    @Transactional(readOnly = true)
    override fun execute(reqDto: GetEntryCardsReqDto): EntryCardsResDto {
        val standardIds = reqDto.standardParticipantIds.distinct()
        val traineeIds = reqDto.traineeIds.distinct()
        val participants =
            if (standardIds.isEmpty()) {
                emptyMap()
            } else {
                standardParticipantRepository
                    .findEntryCardsByExpoIdAndIdIn(
                        reqDto.expoId,
                        standardIds,
                    ).associateBy {
                        it.id
                    }
            }
        val trainees =
            if (traineeIds.isEmpty()) {
                emptyMap()
            } else {
                traineeRepository
                    .findEntryCardsByExpoIdAndIdIn(
                        reqDto.expoId,
                        traineeIds,
                    ).associateBy {
                        it.id
                    }
            }
        return EntryCardsResDto(
            standardParticipants =
                standardIds.mapNotNull(participants::get).map {
                    StandardParticipantEntryCardResDto(
                        participantId = it.id,
                        name = it.name,
                        phoneNumber = it.phoneNumber,
                        personalInformationStatus = it.personalInformationStatus,
                        occupation = it.occupation,
                        school = it.school,
                    )
                },
            trainees =
                traineeIds.mapNotNull(trainees::get).map {
                    TraineeEntryCardResDto(
                        traineeId = it.id,
                        name = it.name,
                        phoneNumber = it.phoneNumber,
                        personalInformationStatus = it.personalInformationStatus,
                        school = it.school,
                    )
                },
        )
    }
}
