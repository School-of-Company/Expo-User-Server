package team.startup.expo.global.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * `springdoc` 문서 설정이다. 문서는 `SWAGGER_ENABLED=true`일 때만 열린다(기본은 꺼짐, `application.yaml`과 `SecurityConfig` 참고).
 *
 * Gateway의 통합 문서 화면이 `/auth/v3/api-docs`를 읽는다. 서비스 간 호출용 `/internal` 하위 경로는 Gateway에 라우팅하지 않는
 * 경로라 `SWAGGER_INCLUDE_INTERNAL=true`일 때만 문서에 넣고, 그때만 `X-Internal-Token` 인증 방식을 표시한다.
 */
@Configuration
class SwaggerConfig {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            // Gateway는 프록시할 때 Host를 인스턴스 주소로 바꾼다. 요청의 Host로 서버 주소를 만들면 내부 주소가 문서에 나오므로 상대 경로로 고정한다
            .servers(listOf(Server().url("/")))
            .info(
                Info()
                    .title("Expo User Server API")
                    .version("v1")
                    .description(
                        "인증, 관리자, 연수자, 참가자 서비스. 오류 응답은 `{status, message}` 형식이다. " +
                            "서비스 간 호출(`/internal/**`)은 `X-Internal-Token`으로만 인증하며 Gateway로 노출하지 않고, 설정을 켠 경우에만 이 문서에 나온다.",
                    ),
            ).components(
                Components()
                    .addSecuritySchemes(
                        BEARER_AUTH,
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("관리자 access token. 토큰 값에는 `Bearer `를 붙이지 않고 문서 화면이 붙인다."),
                    ).addSecuritySchemes(
                        INTERNAL_TOKEN,
                        SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY)
                            .`in`(SecurityScheme.In.HEADER)
                            .name("X-Internal-Token")
                            .description("서비스 간 호출용 공유 토큰. 사용자 토큰은 `/internal/**`에서 무시한다."),
                    ).addSecuritySchemes(
                        PARTICIPANT_SESSION,
                        SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY)
                            .`in`(SecurityScheme.In.COOKIE)
                            .name("PARTICIPANT_SESSION")
                            .description("참가자 전화 인증 세션 쿠키(`HttpOnly`). 쿠키 이름은 설정(`PARTICIPANT_SESSION_COOKIE_NAME`)으로 바뀔 수 있다."),
                    ),
            )

    /** `/internal` 하위 경로를 빼고, 그 경로에서만 쓰던 스키마·태그·인증 방식도 함께 뺀다. 켜면 아무것도 바꾸지 않는다. */
    @Bean
    fun internalFilter(
        @Value("\${swagger.include-internal:false}") includeInternal: Boolean,
    ): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            if (!includeInternal) {
                OpenApiInternalFilter.remove(openApi)
            }
        }

    companion object {
        const val BEARER_AUTH = "bearerAuth"
        const val INTERNAL_TOKEN = "internalToken"
        const val PARTICIPANT_SESSION = "participantSession"
    }
}
