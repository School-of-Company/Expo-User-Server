package team.startup.expo.domain.participation.presentation.dto.response

/** `informationStatus`는 개인정보 수집 동의 여부(`personalInformationStatus`)다. */
data class ParticipantResDto(
    val id: Long,
    val name: String,
    val phoneNumber: String,
    val informationStatus: Boolean,
)
