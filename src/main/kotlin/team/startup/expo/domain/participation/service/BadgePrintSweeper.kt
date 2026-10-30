package team.startup.expo.domain.participation.service

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 출력대가 모두 멈춰 claim이 오지 않아도 결과 없는 점유가 확인 필요 목록에 보이도록 주기적으로 만료시킨다. claim도 같은 만료를
 * 먼저 하므로 이 작업이 늦어도 같은 작업이 두 번 나가지는 않는다. `badge-print.sweeper.enabled=false`로 끌 수 있다.
 */
@Component
@ConditionalOnProperty(prefix = "badge-print.sweeper", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class BadgePrintSweeper(
    private val badgePrintService: BadgePrintService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${badge-print.sweeper.interval-ms:5000}")
    fun sweep() {
        try {
            val expired = badgePrintService.expireClaims()
            if (expired > 0) log.warn("결과 없는 명찰 출력 점유를 확인 필요로 닫음: count={}", expired)
        } catch (exception: Exception) {
            log.warn("명찰 출력 점유 만료 실패", exception)
        }
    }
}
