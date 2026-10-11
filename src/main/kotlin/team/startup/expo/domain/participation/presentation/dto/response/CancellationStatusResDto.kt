package team.startup.expo.domain.participation.presentation.dto.response

/**
 * 신청 취소 가능 여부를 판단할 근거. [entered]는 날짜와 상관없이 그 박람회에서 입장한 기록이 한 번이라도 있는지이고,
 * [traineeLinked]가 `false`이면 이어진 연수자가 없다는 뜻이며 이때 [linkedTraineeId]는 `null`이다.
 */
data class CancellationStatusResDto(
    val participantId: Long,
    val representative: Boolean,
    val entered: Boolean,
    val traineeLinked: Boolean,
    val linkedTraineeId: Long?,
)
