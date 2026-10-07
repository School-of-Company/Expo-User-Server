package team.startup.expo.domain.participation.presentation.dto.response

/** 일반 참가자를 새로 저장했다는 이벤트. Expo가 신청 인원 집계(멱등 PUT)를 놓친 경우 다시 반영하는 데 쓴다. */
data class StandardParticipantCreatedEvent(
    val eventId: String,
    val expoId: String,
    val participantId: Long,
)
