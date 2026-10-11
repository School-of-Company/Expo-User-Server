package team.startup.expo.domain.auth.presentation

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import team.startup.expo.global.security.ParticipantSessionProperties
import java.time.Duration

/**
 * 참가자 세션 쿠키를 만들고 읽는다. 값은 불투명 ID뿐이고 전화번호는 쿠키에 싣지 않는다. 항상 `HttpOnly`이고 `Secure`와
 * `SameSite`, 도메인은 설정을 따른다. 관리자 JWT는 `Authorization` 헤더를 쓰므로 이 쿠키와 섞이지 않는다.
 */
@Component
class ParticipantSessionCookies(
    private val properties: ParticipantSessionProperties,
) {
    fun issue(
        sessionId: String,
        maxAge: Duration,
    ): ResponseCookie = build(sessionId, maxAge)

    fun clear(): ResponseCookie = build("", Duration.ZERO)

    fun read(request: HttpServletRequest): String? = request.cookies?.firstOrNull { it.name == properties.cookieName }?.value

    private fun build(
        value: String,
        maxAge: Duration,
    ): ResponseCookie =
        ResponseCookie
            .from(properties.cookieName, value)
            .httpOnly(true)
            .secure(properties.secure)
            .sameSite(properties.sameSite)
            .path("/")
            .maxAge(maxAge)
            .apply { if (properties.domain.isNotBlank()) domain(properties.domain) }
            .build()
}
