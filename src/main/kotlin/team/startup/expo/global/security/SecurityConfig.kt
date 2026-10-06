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
import team.startup.expo.global.security.jwt.JwtProvider
import tools.jackson.databind.ObjectMapper

/**
 * 관리자 인증은 `Authorization` 토큰을 직접 검증해 한다(`AccessTokenAuthenticationFilter`). 서비스 간
 * 호출은 `/internal` 하위 경로에서 `X-Internal-Token`으로 한다(`InternalTokenAuthenticationFilter`).
 * 명시하지 않은 경로는 전부 막아 두고, 도메인을 추가할 때 경로별 규칙을 여기에 더한다.
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
        jwtProvider: JwtProvider,
        internalProperties: InternalProperties,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(
                AccessTokenAuthenticationFilter(adminRepository, jwtProvider),
                UsernamePasswordAuthenticationFilter::class.java,
            ).addFilterBefore(InternalTokenAuthenticationFilter(internalProperties), UsernamePasswordAuthenticationFilter::class.java)
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
                    .requestMatchers(HttpMethod.PATCH, "/admin/{admin_id}")
                    .hasAuthority(Authority.ROLE_ADMIN.name)
                    .requestMatchers(HttpMethod.DELETE, "/admin", "/admin/{admin_id}")
                    .hasAuthority(Authority.ROLE_ADMIN.name)
                    .requestMatchers("/internal/**")
                    .hasAuthority(InternalTokenAuthenticationFilter.SERVICE_AUTHORITY)
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
