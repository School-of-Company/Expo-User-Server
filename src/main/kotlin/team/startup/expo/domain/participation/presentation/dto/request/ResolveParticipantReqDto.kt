package team.startup.expo.domain.participation.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import team.startup.expo.domain.participation.entity.ParticipationType

data class ResolveParticipantReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    @field:Pattern(regexp = "^\\d{1,15}$", message = "전화번호는 하이픈 없이 숫자만 입력해야 합니다.")
    val phoneNumber: String,
    val participationType: ParticipationType,
)
