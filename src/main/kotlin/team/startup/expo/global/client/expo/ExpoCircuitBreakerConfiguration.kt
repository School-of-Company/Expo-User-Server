package team.startup.expo.global.client.expo

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class ExpoCircuitBreakerConfiguration {
    /** 최근 20번 중 10번 이상 호출되고 절반 이상 실패하면 10초 동안 열려 Expo를 부르지 않고 바로 실패한다. */
    @Bean
    fun expoCircuitBreaker(): CircuitBreaker =
        CircuitBreaker.of(
            "expo",
            CircuitBreakerConfig
                .custom()
                .slidingWindowSize(WINDOW_SIZE)
                .minimumNumberOfCalls(MIN_CALLS)
                .failureRateThreshold(FAILURE_RATE_THRESHOLD)
                .waitDurationInOpenState(Duration.ofSeconds(OPEN_SECONDS))
                .build(),
        )

    private companion object {
        const val WINDOW_SIZE = 20
        const val MIN_CALLS = 10
        const val FAILURE_RATE_THRESHOLD = 50f
        const val OPEN_SECONDS = 10L
    }
}
