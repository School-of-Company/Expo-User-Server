package team.startup.expo.global.client.expo.projection

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import team.startup.expo.global.client.expo.ExpoPeriod
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 프로젝션에서 박람회 기간과 사전등록 회차를 읽는다. 두 메서드 모두 `null`은 "프로젝션으로 답할 수 없음"이다. 기능이
 * 꺼져 있거나 프로젝션에 그 박람회가 아직 없으면(첫 republish 전) `null`을 돌려주고, 호출자는 Expo를 부른다.
 *
 * 답은 Expo의 동기 API와 같게 맞춘다. tombstone을 받은 박람회는 없는 박람회(404)이고, 삭제 중인 박람회의 기간은
 * 그대로 돌려주지만 그 회차는 없는 회차(409)로 본다.
 */
@Component
class ExpoProjectionReader(
    private val expoProjectionRepository: ExpoProjectionRepository,
    private val properties: ExpoProjectionProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val missLogged = AtomicBoolean(false)

    /** 프로젝션으로 답할 수 있으면 [Period]를, 없으면 `null`을 돌려준다. [Period.period]가 `null`이면 없는 박람회다. */
    fun findPeriod(expoId: String): Period? {
        val row = lookup(expoId) ?: return null
        if (row.deleted) return Period(null)
        return Period(ExpoPeriod(row.startedDay!!, row.finishedDay!!))
    }

    /** 프로젝션으로 답할 수 있으면 회차가 그 박람회의 회차인지를, 없으면 `null`을 돌려준다. */
    fun sessionExists(
        expoId: String,
        sessionId: Long,
    ): Boolean? {
        val row = lookup(expoId) ?: return null
        if (row.deleted || row.deleting) return false
        return expoProjectionRepository.sessionExists(expoId, sessionId)
    }

    data class Period(
        val period: ExpoPeriod?,
    )

    private fun lookup(expoId: String): ExpoProjectionRow? {
        if (!properties.enabled) return null
        val row = expoProjectionRepository.find(expoId)
        // 첫 republish 전에는 박람회마다 없을 수 있어 매번 남기면 로그가 넘친다. 프로세스마다 처음 한 번만 남기고 요청 id는 남기지 않는다
        if (row == null && missLogged.compareAndSet(false, true)) {
            log.warn("expo projection miss, falling back to expo service call (logged once)")
        }
        return row
    }
}
