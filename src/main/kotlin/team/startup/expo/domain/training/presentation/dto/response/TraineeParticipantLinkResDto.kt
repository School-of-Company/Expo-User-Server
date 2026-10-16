package team.startup.expo.domain.training.presentation.dto.response

/** 처리 뒤 연수자의 연결 상태. [participantId]가 `null`이면 이어지지 않았고, [changed]가 `false`이면 이미 그 상태여서 바꾸지 않았다. */
data class TraineeParticipantLinkResDto(
    val traineeId: Long,
    val participantId: Long?,
    val changed: Boolean,
)
