package team.startup.expo.global.client.expo

import feign.FeignException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import team.startup.expo.global.exception.ExpectedException
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Expo 서비스에서 박람회 기간을 읽는다. v1은 같은 DB를 조회했지만 이제는 서비스 간 호출이다.
 *
 * 박람회가 없는 것(`null`)과 호출 실패(503)를 구분한다. 장애를 "박람회 없음"으로 돌려보내면 정상적인
 * 조회가 404로 거절되기 때문이다. 404는 정상 응답으로 취급해 서킷브레이커의 실패로 세지 않는다. 존재하지 않는
 * 박람회 id를 반복해서 조회해도 회로가 열리지 않게 하기 위해서다. 시간 제한은 Feign의 연결·읽기 제한으로 건다.
 */
@Component
class ExpoPeriodReader(
    private val expoClient: ExpoClient,
    private val circuitBreaker: CircuitBreaker,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun find(expoId: String): ExpoPeriod? {
        val lookup =
            try {
                // Resilience4j는 null을 돌려주는 공급자를 받지 못하므로 "박람회 없음"도 값으로 감싼다
                circuitBreaker.executeSupplier { Lookup(fetch(expoId)) }
            } catch (e: Exception) {
                // 요청 id나 응답 본문은 남기지 않고 원인 종류만 남긴다
                log.warn("expo service call failed: {}", e.javaClass.simpleName)
                throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "박람회 정보를 가져올 수 없습니다.")
            }
        return lookup.response?.let(::toPeriod)
    }

    private data class Lookup(
        val response: ExpoPeriodResDto?,
    )

    private fun fetch(expoId: String): ExpoPeriodResDto? =
        try {
            expoClient.getPeriod(expoId)
        } catch (e: FeignException.NotFound) {
            null
        }

    private fun toPeriod(response: ExpoPeriodResDto): ExpoPeriod =
        try {
            ExpoPeriod(LocalDate.parse(response.startedDay), LocalDate.parse(response.finishedDay))
        } catch (e: DateTimeParseException) {
            log.error("expo service returned an unparsable period")
            throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "박람회 정보를 가져올 수 없습니다.")
        }
}
