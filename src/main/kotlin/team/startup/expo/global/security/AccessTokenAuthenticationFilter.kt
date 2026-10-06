package team.startup.expo.global.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.global.security.jwt.JwtProvider

/**
 * `Authorization: Bearer <access token>`을 직접 검증해 관리자를 인증한다. gateway가 전달하는 `X-User-Id`나
 * `X-User-Role` 같은 헤더는 읽지 않는다. 이 서비스는 다른 서비스가 직접 접속할 수 있어서, 헤더는 누구나
 * 넣을 수 있지만 우리가 서명한 토큰은 아무나 만들 수 없기 때문이다. 이 필터가 있으면 외부에서 이 서비스로
 * 직접 닿는지 여부에 관계없이 관리자 경로는 보호된다.
 *
 * 권한은 토큰의 `role`이 아니라 DB의 `Admin`에서 읽는다. 토큰이 유효한 동안 탈퇴하거나 권한이 바뀐
 * 관리자를 즉시 반영하기 위해서다. 토큰이 없거나 올바르지 않거나 관리자가 없으면 인증하지 않는다.
 * `/internal` 하위 경로는 서비스 간 호출이라 사용자 토큰을 보지 않는다.
 */
class AccessTokenAuthenticationFilter(
    private val adminRepository: AdminRepository,
    private val jwtProvider: JwtProvider,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI.startsWith(InternalTokenAuthenticationFilter.INTERNAL_PATH_PREFIX)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        request
            .getHeader(AUTHORIZATION_HEADER)
            ?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)
            ?.trim()
            ?.let(jwtProvider::verifyAdminId)
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

    private companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer "
    }
}
