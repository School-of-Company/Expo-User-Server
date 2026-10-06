package team.startup.expo.domain.participation.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import team.startup.expo.domain.participation.entity.ParticipationType

data class ResolveParticipantReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    @field:Pattern(regexp = "^[0-9\\- ]{1,20}$", message = "전화번호는 숫자와 하이픈만 입력할 수 있습니다.")
    val phoneNumber: String,
    val participationType: ParticipationType,
)
