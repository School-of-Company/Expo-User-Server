package team.startup.expo.domain.participation.presentation.dto.response

import team.startup.expo.domain.participation.entity.Occupation

/** 입장 목록용 카드. 없는 id와 다른 박람회의 id는 빠진다. */
data class EntryCardsResDto(
    val standardParticipants: List<StandardParticipantEntryCardResDto>,
    val trainees: List<TraineeEntryCardResDto>,
)

/** 동행자는 `phoneNumber`가 없다. */
data class StandardParticipantEntryCardResDto(
    val participantId: Long,
    val name: String,
    val phoneNumber: String?,
    val personalInformationStatus: Boolean,
    val occupation: Occupation?,
    val school: String?,
)

/** 연수자는 `occupation`이 없다(연수자는 모두 교사). */
data class TraineeEntryCardResDto(
    val traineeId: Long,
    val name: String,
    val phoneNumber: String?,
    val personalInformationStatus: Boolean,
    val school: String?,
)
