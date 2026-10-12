package team.startup.expo.domain.participation.presentation.dto.response

/**
 * 관리자 취소용 상태. [entered]는 날짜와 상관없이 그 박람회에서 입장한 기록이 한 번이라도 있는지이고, [traineeLinked]가
 * `false`이면 이어진 연수자가 없다는 뜻이며 이때 [linkedTraineeId]는 `null`이다.
 */
data class AdminCancellationStatusResDto(
    val participantId: Long,
    val entered: Boolean,
    val traineeLinked: Boolean,
    val linkedTraineeId: Long?,
)
