package team.startup.expo.global.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import team.startup.expo.global.client.expo.ExpoAuthorization
import team.startup.expo.global.exception.ExpectedException

@Configuration
class ExpoAccessConfiguration(
    @param:Lazy
    private val authorization: ExpoAuthorization,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry
            .addInterceptor(
                object : HandlerInterceptor {
                    override fun preHandle(
                        request: HttpServletRequest,
                        response: HttpServletResponse,
                        handler: Any,
                    ): Boolean {
                        val variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<*, *>
                        val expoId = variables?.get("expo_id") as? String ?: return true
                        val pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)?.toString().orEmpty()
                        val badge = pattern.startsWith("/admin/badge-prints/")
                        val staff = SecurityContextHolder.getContext().authentication?.principal as? StaffPrincipal
                        if (staff != null && (!badge || staff.expoId != expoId)) {
                            throw ExpectedException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.")
                        }
                        authorization.requireAccess(expoId, request.getHeader("Authorization"), if (badge) "CHECKIN" else null)
                        return true
                    }
                },
            ).addPathPatterns("/participant/{expo_id}", "/trainee/{expo_id}", "/admin/badge-prints/{expo_id}/**")
    }
}
