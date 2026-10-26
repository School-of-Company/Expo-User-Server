package team.startup.expo.global.logging

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

/** 접근 로그 필터를 보안 필터 체인(-100)보다 앞에 등록한다. `logging.http-access.enabled=false`면 등록하지 않는다. */
@Configuration
@ConditionalOnBooleanProperty(name = ["logging.http-access.enabled"], matchIfMissing = true)
class HttpAccessLogConfig {
    @Bean
    fun httpAccessLogFilter(): FilterRegistrationBean<HttpAccessLogFilter> =
        FilterRegistrationBean(HttpAccessLogFilter()).apply {
            order = Ordered.HIGHEST_PRECEDENCE + 2
        }
}
