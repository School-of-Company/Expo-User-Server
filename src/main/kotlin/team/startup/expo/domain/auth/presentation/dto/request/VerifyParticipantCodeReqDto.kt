package team.startup.expo.domain.auth.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class VerifyParticipantCodeReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val expoId: String,
    @field:Pattern(regexp = PHONE_PATTERN)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val phoneNumber: String,
    @field:Pattern(regexp = "^\\d{4}$")
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val code: String,
) {
    // 전화번호와 인증번호가 로그에 남지 않게 한다
    override fun toString() = "VerifyParticipantCodeReqDto(expoId=$expoId, phoneNumber=***, code=***)"
}
