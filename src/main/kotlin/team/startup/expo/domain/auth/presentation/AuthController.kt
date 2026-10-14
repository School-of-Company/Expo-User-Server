package team.startup.expo.domain.auth.presentation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
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

@Tag(name = "관리자 인증", description = "가입, 로그인, 토큰 재발급, 로그아웃")
@RestController
@RequestMapping("/auth")
class AuthController(
    private val signUpService: SignUpService,
    private val signInService: SignInService,
    private val reissueTokenService: ReissueTokenService,
    private val logoutService: LogoutService,
) {
    @Operation(
        summary = "회원가입",
        description =
            "가입한 관리자는 승인 대기(PENDING) 상태이며 기존 관리자가 승인해야 로그인할 수 있습니다. 전화번호로 받은 인증번호(`code`)를 " +
                "함께 보내야 하며, 인증번호가 틀리면 400, 인증 요청 내역이 없거나 만료되면 404, 시도 횟수를 넘으면 429, " +
                "인증 서비스에 연결할 수 없으면 503입니다.",
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun signUp(
        @Valid @RequestBody reqDto: SignUpReqDto,
    ) {
        signUpService.execute(reqDto)
    }

    @Operation(
        summary = "로그인",
        description =
            "없는 닉네임과 틀린 비밀번호는 구분 없이 같은 400이고, 승인 전 관리자는 비밀번호가 맞을 때만 403입니다. 같은 닉네임으로 " +
                "짧은 시간에 여러 번 실패하면 비밀번호가 맞아도 429로 잠시 막으며, 응답의 `Retry-After` 헤더에 남은 시간(초)이 있습니다. " +
                "한 관리자가 여러 기기에서 동시에 로그인할 수 있으며 기기마다 refresh token이 따로입니다(최대 `JWT_MAX_REFRESH_TOKENS`개, 넘으면 가장 오래된 기기부터 로그아웃됩니다).",
    )
    @ApiResponse(
        responseCode = "429",
        description = "로그인 시도가 너무 많음",
        headers = [Header(name = "Retry-After", description = "다시 시도할 수 있기까지 남은 시간(초)", schema = Schema(type = "integer"))],
    )
    @PostMapping("/signin")
    fun signIn(
        @Valid @RequestBody reqDto: SignInReqDto,
    ): TokenResDto = signInService.execute(reqDto)

    @Operation(summary = "토큰 재발급", description = "RefreshToken 헤더(Bearer 형식)로 새 토큰 쌍을 발급하며 이전 refresh token은 무효가 됩니다.")
    @PatchMapping
    fun reissueToken(
        @RequestHeader("RefreshToken") refreshToken: String,
    ): TokenResDto = reissueTokenService.execute(refreshToken)

    @Operation(
        summary = "로그아웃",
        description =
            "`RefreshToken` 헤더(`Bearer <refresh token>`)를 보내면 그 기기만 로그아웃하고 다른 기기는 유지합니다. 헤더가 없으면 " +
                "이 관리자의 모든 기기를 로그아웃합니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @AuthenticationPrincipal principal: AdminPrincipal,
        @RequestHeader(name = "RefreshToken", required = false) refreshToken: String?,
    ) {
        logoutService.execute(principal.id, refreshToken)
    }
}
