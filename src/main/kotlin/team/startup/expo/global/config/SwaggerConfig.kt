package team.startup.expo.global.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * `springdoc` 문서 설정이다. 문서는 `SWAGGER_ENABLED=true`일 때만 열린다(기본은 꺼짐, `application.yaml`과 `SecurityConfig` 참고).
 *
 * 공개 API(Gateway를 거쳐 클라이언트가 부르는 경로)와 서비스 간 호출용 `/internal` 하위 경로를 문서 그룹으로 나눈다. `/internal`
 * 하위 경로는 Gateway에 라우팅하지 않는 경로라 공개 그룹에 섞이지 않게 하고, 인증 방식도 `X-Internal-Token`으로 따로 표시한다.
 */
@Configuration
class SwaggerConfig {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Expo User Server API")
                    .version("v1")
                    .description(
                        "인증, 관리자, 연수자, 참가자 서비스. 오류 응답은 `{status, message}` 형식이다. " +
                            "서비스 간 호출(`/internal/**`)은 `X-Internal-Token`으로만 인증하며 Gateway로 노출하지 않는다.",
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

    @Bean
    fun publicApi(): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .group("public")
            .displayName("공개 API (Gateway 경유)")
            .pathsToMatch("/**")
            .pathsToExclude("/internal/**")
            .build()

    @Bean
    fun internalApi(): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .group("internal")
            .displayName("서비스 간 내부 API (X-Internal-Token)")
            .pathsToMatch("/internal/**")
            .build()

    companion object {
        const val BEARER_AUTH = "bearerAuth"
        const val INTERNAL_TOKEN = "internalToken"
        const val PARTICIPANT_SESSION = "participantSession"
    }
}
