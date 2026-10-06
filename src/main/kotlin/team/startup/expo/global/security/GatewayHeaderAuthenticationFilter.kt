package team.startup.expo.global.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import team.startup.expo.domain.user.repository.AdminRepository

/**
 * gateway가 검증한 JWT의 `sub`를 `X-User-Id`로 전달한다. 토큰은 여기서 다시 파싱하지 않는다.
 *
 * 헤더는 gateway 경로에서만 믿을 수 있다. 서비스 간 직접 호출은 gateway를 거치지 않으므로 이 헤더를
 * 신뢰하지 말아야 하고, 외부에서 이 서비스에 직접 닿을 수 없어야 한다.
 *
 * 권한은 `X-User-Role`이 아니라 DB의 `Admin`에서 읽는다. 토큰이 유효한 동안 탈퇴하거나 권한이 바뀐
 * 관리자를 즉시 반영하기 위해서다. 헤더가 없거나 값이 올바르지 않거나 관리자가 없으면 인증하지 않는다.
 */
class GatewayHeaderAuthenticationFilter(
    private val adminRepository: AdminRepository,
) : OncePerRequestFilter() {
    // 서비스 간 호출은 gateway를 거치지 않으므로 이 경로에서는 X-User-Id를 신뢰하지 않는다
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI.startsWith(InternalTokenAuthenticationFilter.INTERNAL_PATH_PREFIX)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        request
            .getHeader(USER_ID_HEADER)
            ?.toLongOrNull()
            ?.let { adminRepository.findById(it).orElse(null) }
            ?.let { admin ->
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(
                        AdminPrincipal(requireNotNull(admin.id), admin.authority),
                        null,
                        listOf(SimpleGrantedAuthority(admin.authority.name)),
                    )
            }
        filterChain.doFilter(request, response)
    }

    companion object {
        const val USER_ID_HEADER = "X-User-Id"
    }
}
