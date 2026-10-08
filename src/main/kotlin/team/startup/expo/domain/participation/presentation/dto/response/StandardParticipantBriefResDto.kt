package team.startup.expo.domain.participation.presentation.dto.response

data class StandardParticipantBriefResDto(
    val participantId: Long,
    val name: String,
    val phoneNumber: String?,
    // 문자를 받을 번호. 본인 번호가 없는 동행자는 대표자 번호이고, 대표자 번호도 없으면 null이다
    val notificationPhoneNumber: String?,
    val personalInformationStatus: Boolean,
)
