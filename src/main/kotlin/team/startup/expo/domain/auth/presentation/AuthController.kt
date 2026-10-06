package team.startup.expo.domain.auth.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.auth.presentation.dto.request.SignInReqDto
import team.startup.expo.domain.auth.presentation.dto.request.SignUpReqDto
import team.startup.expo.domain.auth.presentation.dto.response.TokenResDto
import team.startup.expo.domain.auth.service.SignInService
import team.startup.expo.domain.auth.service.SignUpService

@RestController
@RequestMapping("/auth")
class AuthController(
    private val signUpService: SignUpService,
    private val signInService: SignInService,
) {
    @Operation(summary = "회원가입", description = "가입한 관리자는 승인 대기(PENDING) 상태이며 기존 관리자가 승인해야 로그인할 수 있습니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun signUp(
        @Valid @RequestBody reqDto: SignUpReqDto,
    ) {
        signUpService.execute(reqDto)
    }

    @Operation(summary = "로그인")
    @PostMapping("/signin")
    fun signIn(
        @Valid @RequestBody reqDto: SignInReqDto,
    ): TokenResDto = signInService.execute(reqDto)
}
