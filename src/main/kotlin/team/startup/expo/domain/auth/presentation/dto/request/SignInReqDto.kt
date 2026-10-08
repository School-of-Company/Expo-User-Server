package team.startup.expo.domain.auth.presentation.dto.request

import jakarta.validation.constraints.NotBlank

data class SignInReqDto(
    @field:NotBlank
    val nickname: String,
    @field:NotBlank
    val password: String,
) {
    override fun toString() = "SignInReqDto(nickname=$nickname, password=***)"
}
