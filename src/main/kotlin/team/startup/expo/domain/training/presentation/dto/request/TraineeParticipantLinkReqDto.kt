package team.startup.expo.domain.training.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class TraineeParticipantLinkReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    @field:NotNull
    @field:Positive
    val traineeId: Long?,
    @field:NotNull
    @field:Positive
    val participantId: Long?,
    /** 확인·정정을 승인한 관리자. 신청 서비스가 박람회 관리 권한을 검증한 뒤 넘기고, 이 서비스는 `Admin` 행의 권한을 다시 확인한다. */
    @field:NotNull
    @field:Positive
    val actorAdminId: Long?,
    /** 운영자가 같은 사람으로 확인한 근거(확인 방법·대조한 자료). 감사 기록에 그대로 남는다. */
    @field:NotBlank
    @field:Size(max = 500)
    val reason: String,
)
