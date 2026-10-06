package team.startup.expo.domain.auth.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.auth.presentation.dto.request.SignInReqDto
import team.startup.expo.domain.auth.presentation.dto.request.SignUpReqDto
import team.startup.expo.domain.auth.presentation.dto.response.TokenResDto
import team.startup.expo.domain.auth.service.LogoutService
import team.startup.expo.domain.auth.service.ReissueTokenService
import team.startup.expo.domain.auth.service.SignInService
import team.startup.expo.domain.auth.service.SignUpService
import team.startup.expo.global.security.AdminPrincipal

@RestController
@RequestMapping("/auth")
class AuthController(
    private val signUpService: SignUpService,
    private val signInService: SignInService,
    private val reissueTokenService: ReissueTokenService,
    private val logoutService: LogoutService,
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

    @Operation(summary = "토큰 재발급", description = "RefreshToken 헤더(Bearer 형식)로 새 토큰 쌍을 발급하며 이전 refresh token은 무효가 됩니다.")
    @PatchMapping
    fun reissueToken(
        @RequestHeader("RefreshToken") refreshToken: String,
    ): TokenResDto = reissueTokenService.execute(refreshToken)

    @Operation(summary = "로그아웃", description = "저장된 refresh token을 삭제합니다.")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @AuthenticationPrincipal principal: AdminPrincipal,
    ) {
        logoutService.execute(principal.id)
    }
}
