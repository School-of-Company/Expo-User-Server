package team.startup.expo.domain.participation.presentation.dto.response

import team.startup.expo.domain.participation.entity.Occupation

data class StandardParticipantBriefResDto(
    val participantId: Long,
    val name: String,
    val phoneNumber: String?,
    // 문자를 받을 번호. 본인 번호가 없는 동행자는 대표자 번호이고, 대표자 번호도 없으면 null이다
    val notificationPhoneNumber: String?,
    val personalInformationStatus: Boolean,
    val occupation: Occupation?,
    // 동행자이면 대표자의 id, 대표자 본인이면 null이다
    val representativeId: Long?,
    // 이어진 연수자가 없으면 null이다
    val linkedTraineeId: Long?,
)
