package team.startup.expo.global.client.notification

import feign.RequestInterceptor
import org.springframework.context.annotation.Bean

/**
 * `@Configuration`을 붙이지 않는다. 붙이면 컴포넌트 스캔으로 전역에 등록되어 다른 Feign 클라이언트에도
 * 내부 토큰이 실려 나간다. 이 클래스는 [NotificationClient]에만 적용된다.
 */
class NotificationClientConfiguration {
    @Bean
    fun notificationInternalTokenInterceptor(properties: NotificationClientProperties): RequestInterceptor =
        RequestInterceptor { template ->
            if (properties.internalToken.isNotEmpty()) template.header(TOKEN_HEADER, properties.internalToken)
        }

    private companion object {
        const val TOKEN_HEADER = "X-Internal-Token"
    }
}
