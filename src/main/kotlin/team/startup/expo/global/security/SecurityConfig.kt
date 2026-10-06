package team.startup.expo.global.security

import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import team.startup.expo.domain.user.entity.Authority
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.global.exception.ErrorResponse
import tools.jackson.databind.ObjectMapper

/**
 * 인증은 gateway가 검증해 전달한 `X-User-Id`로 한다(`GatewayHeaderAuthenticationFilter`).
 * 이 서비스는 토큰을 파싱하지 않는다. 명시하지 않은 경로는 전부 막아 두고, 도메인을 추가할 때
 * 경로별 규칙을 여기에 더한다.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        objectMapper: ObjectMapper,
        adminRepository: AdminRepository,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(GatewayHeaderAuthenticationFilter(adminRepository), UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint { _, response, _ ->
                        writeError(objectMapper, response, HttpServletResponse.SC_UNAUTHORIZED, "인증이 필요합니다.")
                    }.accessDeniedHandler { _, response, _ ->
                        writeError(objectMapper, response, HttpServletResponse.SC_FORBIDDEN, "접근 권한이 없습니다.")
                    }
            }.authorizeHttpRequests { requests ->
                requests
                    .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/auth", "/auth/signin")
                    .permitAll()
                    .requestMatchers(HttpMethod.PATCH, "/auth")
                    .permitAll()
                    .requestMatchers(HttpMethod.DELETE, "/auth")
                    .hasAuthority(Authority.ROLE_ADMIN.name)
                    .requestMatchers(HttpMethod.GET, "/admin", "/admin/my")
                    .hasAuthority(Authority.ROLE_ADMIN.name)
                    .anyRequest()
                    .denyAll()
            }

        return http.build()
    }

    private fun writeError(
        objectMapper: ObjectMapper,
        response: HttpServletResponse,
        status: Int,
        message: String,
    ) {
        response.status = status
        response.characterEncoding = Charsets.UTF_8.name()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        objectMapper.writeValue(response.writer, ErrorResponse(status = status, message = message))
    }
}
