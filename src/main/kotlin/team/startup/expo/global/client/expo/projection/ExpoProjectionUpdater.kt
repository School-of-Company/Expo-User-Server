package team.startup.expo.global.client.expo.projection

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 박람회 변경 이벤트 하나로 그 박람회의 프로젝션 전체를 덮어쓴다(부분 병합하지 않는다).
 *
 * 박람회마다 마지막으로 반영한 `version`을 두고 그 값 이하의 이벤트는 버린다. 같은 `eventId`의 재전송은 같은 `version`이라
 * 함께 걸러지고, 순서가 바뀌어 늦게 온 옛 이벤트도 최신 상태를 덮지 못한다. tombstone은 기간과 회차를 지우되 `version`은
 * 남겨 옛 이벤트로 되살리지 않는다.
 */
@Component
class ExpoProjectionUpdater(
    private val expoProjectionRepository: ExpoProjectionRepository,
) {
    /** 반영했으면 `true`, 이미 같거나 더 새로운 상태가 있어 버렸으면 `false`다. */
    @Transactional
    fun apply(change: ExpoProjectionChange): Boolean {
        if (expoProjectionRepository.upsertIfNewer(change) == 0) return false
        expoProjectionRepository.replaceSessions(change.expoId, change.sessionIds)
        return true
    }
}
