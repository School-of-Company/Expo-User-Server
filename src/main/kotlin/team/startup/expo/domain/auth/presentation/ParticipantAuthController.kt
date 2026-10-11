package team.startup.expo.domain.auth.presentation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.auth.model.ParticipantSessionLookup
import team.startup.expo.domain.auth.presentation.dto.request.SendParticipantCodeReqDto
import team.startup.expo.domain.auth.presentation.dto.request.VerifyParticipantCodeReqDto
import team.startup.expo.domain.auth.presentation.dto.response.ParticipantSessionResDto
import team.startup.expo.domain.auth.service.FindParticipantSessionService
import team.startup.expo.domain.auth.service.LogoutParticipantSessionService
import team.startup.expo.domain.auth.service.SendParticipantCodeService
import team.startup.expo.domain.auth.service.VerifyParticipantCodeService
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.security.ParticipantSessionProperties
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * 행사에 신청하는 참가자의 전화 인증 세션이다. 관리자 로그인(`/auth`)과 별개이며 사용자에게 공개된 경로다.
 * 세션 ID는 `HttpOnly` 쿠키로만 오가고 전화번호는 URL이나 쿠키에 싣지 않는다.
 */
@Tag(name = "참가자 전화 인증 세션", description = "행사 신청자의 전화 인증과 HttpOnly 쿠키 세션")
@RestController
@RequestMapping("/participant-auth")
class ParticipantAuthController(
    private val sendParticipantCodeService: SendParticipantCodeService,
    private val verifyParticipantCodeService: VerifyParticipantCodeService,
    private val findParticipantSessionService: FindParticipantSessionService,
    private val logoutParticipantSessionService: LogoutParticipantSessionService,
    private val cookies: ParticipantSessionCookies,
    private val properties: ParticipantSessionProperties,
) {
    @Operation(summary = "참가자 인증번호 요청", description = "박람회가 있으면 Notification이 전화번호로 인증번호를 보냅니다. 참가자 등록 전에도 요청할 수 있습니다.")
    @PostMapping("/code")
    fun sendCode(
        @Valid @RequestBody reqDto: SendParticipantCodeReqDto,
    ) = sendParticipantCodeService.execute(reqDto)

    @Operation(
        summary = "참가자 인증번호 검증과 세션 발급",
        description = "인증번호가 맞으면 그 박람회에 한정한 세션을 만들고 HttpOnly 쿠키로 보냅니다. 같은 인증번호는 한 번만 쓸 수 있습니다.",
    )
    @PostMapping("/verify")
    fun verify(
        @Valid @RequestBody reqDto: VerifyParticipantCodeReqDto,
    ): ResponseEntity<ParticipantSessionResDto> {
        val issued = verifyParticipantCodeService.execute(reqDto)
        val maxAge = Duration.between(LocalDateTime.now(ZoneOffset.UTC), issued.session.expiresAt).coerceAtLeast(Duration.ZERO)
        return ResponseEntity
            .ok()
            .header(HttpHeaders.SET_COOKIE, cookies.issue(issued.sessionId, maxAge).toString())
            .body(ParticipantSessionResDto(issued.session.expoId, issued.session.expiresAt))
    }

    @Operation(
        summary = "참가자 세션 확인",
        description = "쿠키의 세션이 이 박람회에 유효하면 200입니다. 쿠키가 없으면 401 SESSION_REQUIRED, 만료되었으면 401 SESSION_EXPIRED입니다.",
    )
    @SecurityRequirement(name = "participantSession")
    @GetMapping("/session")
    fun session(
        @RequestParam("expoId") expoId: String,
        request: HttpServletRequest,
    ): ResponseEntity<*> {
        if (expoId.isBlank() || expoId.length > MAX_EXPO_ID_LENGTH) throw ExpectedException(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.")
        val lookup = findParticipantSessionService.execute(cookies.read(request), expoId)
        if (lookup !is ParticipantSessionLookup.Valid) return SessionResponses.error(lookup)
        return ResponseEntity.ok(ParticipantSessionResDto(lookup.session.expoId, lookup.session.expiresAt))
    }

    @Operation(summary = "참가자 세션 로그아웃", description = "세션을 폐기하고 쿠키를 지웁니다. 쿠키가 없거나 이미 없는 세션이어도 204입니다.")
    @SecurityRequirement(name = "participantSession")
    @DeleteMapping("/session")
    fun logout(request: HttpServletRequest): ResponseEntity<Void> {
        logoutParticipantSessionService.execute(cookies.read(request))
        return ResponseEntity
            .noContent()
            .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
            .build()
    }

    private companion object {
        const val MAX_EXPO_ID_LENGTH = 36
    }
}
