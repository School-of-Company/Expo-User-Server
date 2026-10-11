package team.startup.expo.domain.auth.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class SendParticipantCodeReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    // Notification의 인증 API와 같은 형식이다(숫자만, 하이픈 없음)
    @field:Pattern(regexp = PHONE_PATTERN)
    val phoneNumber: String,
) {
    override fun toString() = "SendParticipantCodeReqDto(expoId=$expoId, phoneNumber=***)"
}

internal const val PHONE_PATTERN = "^01\\d{8,9}$"
