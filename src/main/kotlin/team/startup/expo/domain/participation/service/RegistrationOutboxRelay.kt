package team.startup.expo.domain.participation.service

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 주기적으로 아웃박스를 비운다. `registration-events.relay.enabled=false`로 끌 수 있다(브로커 없이 도는 테스트용).
 * 발행 실패는 다음 주기에 다시 시도하므로 여기서는 기록만 하고 삼킨다.
 */
@Component
@ConditionalOnProperty(prefix = "registration-events.relay", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class RegistrationOutboxRelay(
    private val registrationOutboxPublisher: RegistrationOutboxPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${registration-events.relay.interval-ms:2000}")
    fun relay() {
        try {
            registrationOutboxPublisher.publishPending()
        } catch (exception: Exception) {
            log.warn("등록 이벤트 릴레이 실패", exception)
        }
    }
}
