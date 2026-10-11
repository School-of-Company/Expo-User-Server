package team.startup.expo.domain.training.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class ResolveOrCreateTraineeByParticipantReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    @field:NotNull
    @field:Positive
    val participantId: Long?,
    /** 연수원 아이디. 새로 이을 때만 저장하고 모르면 비운다. 이미 이어진 연수자의 값은 덮어쓰지 않는다. */
    @field:Size(min = 1, max = 15)
    val trainingId: String? = null,
    /** 명찰 출력에 쓴다. 새로 이을 때만 저장하며 없으면 참가자의 소속을 쓴다. */
    @field:Size(max = 100)
    val school: String? = null,
)
