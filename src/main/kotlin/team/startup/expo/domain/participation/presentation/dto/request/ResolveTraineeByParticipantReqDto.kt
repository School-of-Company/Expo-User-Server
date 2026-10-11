package team.startup.expo.domain.participation.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class ResolveTraineeByParticipantReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    @field:NotNull
    val participantId: Long?,
    @field:NotBlank
    @field:Size(max = 22)
    val code: String,
) {
    // 개인 code가 로그에 남지 않게 한다
    override fun toString() = "ResolveTraineeByParticipantReqDto(expoId=$expoId, participantId=$participantId, code=***)"
}
