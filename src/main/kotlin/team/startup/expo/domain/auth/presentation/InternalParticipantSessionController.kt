package team.startup.expo.domain.auth.presentation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.auth.model.ParticipantSessionLookup
import team.startup.expo.domain.auth.presentation.dto.request.ResolveParticipantSessionReqDto
import team.startup.expo.domain.auth.presentation.dto.response.ResolvedParticipantSessionResDto
import team.startup.expo.domain.auth.service.FindParticipantSessionService

/**
 * 서비스 간 호출 전용이다. `X-Internal-Token`이 필요하며 gateway에 라우팅하지 않는다. 전화번호는 요청과 응답 본문으로만 다룬다.
 * 신청 서비스가 참가자 쿠키의 세션 ID와 행사로 검증된 전화번호를 얻는다. 클라이언트가 보낸 "인증됨" 값은 믿지 않는다.
 */
@Tag(name = "내부: 참가자 세션", description = "신청 서비스가 세션의 검증된 전화번호를 조회")
@SecurityRequirement(name = "internalToken")
@RestController
@RequestMapping("/internal/participant-sessions")
class InternalParticipantSessionController(
    private val findParticipantSessionService: FindParticipantSessionService,
) {
    @Operation(
        summary = "참가자 세션 조회",
        description =
            "세션 ID와 행사로 검증된 전화번호를 돌려줍니다. 세션이 없으면 401 SESSION_REQUIRED, 만료되었으면 401 SESSION_EXPIRED, " +
                "다른 행사의 세션은 401 SESSION_REQUIRED입니다.",
    )
    @PostMapping("/resolve")
    fun resolve(
        @Valid @RequestBody reqDto: ResolveParticipantSessionReqDto,
    ): ResponseEntity<*> {
        val lookup = findParticipantSessionService.execute(reqDto.sessionId, reqDto.expoId)
        if (lookup !is ParticipantSessionLookup.Valid) return SessionResponses.error(lookup)
        val session = lookup.session
        return ResponseEntity.ok(ResolvedParticipantSessionResDto(session.expoId, session.phoneNumber, session.expiresAt))
    }
}
