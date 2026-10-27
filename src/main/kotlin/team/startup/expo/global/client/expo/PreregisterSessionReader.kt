package team.startup.expo.global.client.expo

import feign.FeignException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import team.startup.expo.global.client.expo.projection.ExpoProjectionReader
import team.startup.expo.global.exception.ExpectedException

/**
 * Expo 서비스에서 사전등록 회차가 그 박람회의 회차인지 확인한다. [ExpoPeriodReader]와 같게 회차가 없는 것(`false`)과
 * 호출 실패(503)를 구분하고, 404는 서킷브레이커의 실패로 세지 않는다. Expo는 다른 박람회의 회차도 404로 답한다.
 * 삭제 중인 박람회의 회차(409)는 곧 없어질 회차라 없는 회차와 같게 본다.
 *
 * 프로젝션이 켜져 있으면 먼저 로컬 프로젝션([ExpoProjectionReader])을 읽고, 프로젝션에 없는 박람회만 Expo를 부른다.
 */
@Component
class PreregisterSessionReader(
    private val expoClient: ExpoClient,
    private val circuitBreaker: CircuitBreaker,
    private val expoProjectionReader: ExpoProjectionReader,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun exists(
        expoId: String,
        sessionId: Long,
    ): Boolean {
        expoProjectionReader.sessionExists(expoId, sessionId)?.let { return it }
        return try {
            circuitBreaker.executeSupplier { fetch(expoId, sessionId) }
        } catch (e: Exception) {
            // 요청 id나 응답 본문은 남기지 않고 원인 종류만 남긴다
            log.warn("expo service call failed: {}", e.javaClass.simpleName)
            throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "박람회 정보를 가져올 수 없습니다.")
        }
    }

    private fun fetch(
        expoId: String,
        sessionId: Long,
    ): Boolean =
        try {
            val session = expoClient.getPreregisterSession(expoId, sessionId)
            session.id == sessionId && session.expoId == expoId
        } catch (e: FeignException.NotFound) {
            false
        } catch (e: FeignException.Conflict) {
            false
        }
}
