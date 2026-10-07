package team.startup.expo.domain.training.presentation.dto.response

/** [created]가 `false`이면 이미 있던 연수자이고 요청 값으로 아무것도 바꾸지 않았다. */
data class ResolveOrCreateTraineeResDto(
    val traineeId: Long,
    val created: Boolean,
)
