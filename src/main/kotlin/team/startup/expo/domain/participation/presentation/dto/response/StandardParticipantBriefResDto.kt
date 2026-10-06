package team.startup.expo.domain.participation.presentation.dto.response

data class StandardParticipantBriefResDto(
    val participantId: Long,
    val name: String,
    val phoneNumber: String,
    val personalInformationStatus: Boolean,
)
