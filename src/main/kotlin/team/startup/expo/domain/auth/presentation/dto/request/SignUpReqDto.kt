package team.startup.expo.domain.auth.presentation.dto.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class SignUpReqDto(
    @field:NotBlank
    @field:Size(max = 10)
    val name: String,
    @field:NotBlank
    @field:Size(max = 50)
    val nickname: String,
    @field:NotBlank
    @field:Size(max = 100)
    @field:Email(message = "이메일 형식에 맞지 않습니다.")
    val email: String,
    @field:NotBlank
    @field:Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[\\W_])[A-Za-z\\d\\W_]{8,24}$",
        message = "비밀번호는 8~24자이며, 대문자, 소문자, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다.",
    )
    val password: String,
    @field:NotBlank
    @field:Pattern(regexp = "^01\\d{8,9}$", message = "전화번호는 하이픈 없이 숫자만 입력해야 합니다. (예: 01012341234)")
    val phoneNumber: String,
) {
    // 비밀번호가 로그나 예외 메시지에 남지 않도록 가린다
    override fun toString() = "SignUpReqDto(name=$name, nickname=$nickname, email=$email, password=***, phoneNumber=$phoneNumber)"
}
