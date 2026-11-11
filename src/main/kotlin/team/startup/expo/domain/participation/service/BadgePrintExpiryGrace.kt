package team.startup.expo.domain.participation.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 기동 직후 점유 만료 유예(#246). 이 인스턴스가 요청을 받기 시작한 뒤 [graceSeconds]초(점유 만료 시간과 같은 30초)가 지나기
 * 전에는 주기 만료와 claim 안의 만료가 결과 없는 점유를 닫지 않는다. 서버가 내려가 있던 동안 결과를 보고하지 못한 출력대가
 * 다시 보고할 기회를 먼저 준다.
 *
 * 만료 기준을 `max(claimed_at, 기동 시각) + 30초`로 계산하는 것과 같다. 기동 뒤 점유는 유예 동안 30초가 지날 수 없기 때문이다.
 * 기동 시각은 벽시계가 아니라 이 JVM의 단조 시계(`System.nanoTime`)로 재므로 인스턴스·DB 시계 차이와 상관없다. 유예는 인스턴스마다
 * 따로다. 다른 인스턴스가 계속 떠 있었다면 보고는 그쪽으로 갈 수 있었으므로 그쪽 만료는 그대로 돈다. 모두 함께 내려갔다
 * 올라오면 각자 유예한다. `badge-print.expiry-grace-seconds=0`이면 끈다.
 */
@Component
class BadgePrintExpiryGrace(
    @Value("\${badge-print.expiry-grace-seconds:30}") graceSeconds: Long,
) {
    private val grace: Duration = Duration.ofSeconds(graceSeconds)

    @Volatile
    private var readyAtNanos: Long? = null

    /** 단조 시계. 테스트가 바꾼다. */
    internal var nanoTime: () -> Long = System::nanoTime

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        readyAtNanos = nanoTime()
    }

    /** 지금 만료를 미뤄야 하면 `true`다. 요청을 받기 전(기동 중)도 미룬다. */
    fun active(): Boolean {
        if (grace.isZero || grace.isNegative) return false
        val readyAt = readyAtNanos ?: return true
        return nanoTime() - readyAt < grace.toNanos()
    }
}
