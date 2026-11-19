package team.startup.expo.global.client.expo

import feign.FeignException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import team.startup.expo.global.client.expo.projection.ExpoProjectionReader
import team.startup.expo.global.exception.ExpectedException
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Expo 서비스에서 박람회 기간을 읽는다. v1은 같은 DB를 조회했지만 이제는 서비스 간 호출이다.
 *
 * 박람회가 없는 것(`null`)과 호출 실패(503)를 구분한다. 장애를 "박람회 없음"으로 돌려보내면 정상적인
 * 조회가 404로 거절되기 때문이다. 404는 정상 응답으로 취급해 서킷브레이커의 실패로 세지 않는다. 존재하지 않는
 * 박람회 id를 반복해서 조회해도 회로가 열리지 않게 하기 위해서다. 시간 제한은 Feign의 연결·읽기 제한으로 건다.
 *
 * 프로젝션이 켜져 있으면 먼저 로컬 프로젝션([ExpoProjectionReader])을 읽고, 프로젝션에 없는 박람회만 Expo를 부른다.
 * 기능 설정([readFeatures])은 프로젝션을 거치지 않고 항상 Expo를 부른다. 프로젝션이 늦어 꺼진 기능을 허용하면 안 되기 때문이다.
 */
@Component
class ExpoPeriodReader(
    private val expoClient: ExpoClient,
    private val circuitBreaker: CircuitBreaker,
    private val expoProjectionReader: ExpoProjectionReader,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun find(expoId: String): ExpoPeriod? {
        expoProjectionReader.findPeriod(expoId)?.let { return it.period }
        return call(expoId)?.let(::toPeriod)
    }

    /**
     * 신규 쓰기 검사용 기능 설정을 Expo에서 읽는다. DB 잠금·트랜잭션 밖에서 불러야 한다. 없는 박람회는 404이고,
     * 호출 실패와 `features`가 없거나 해석할 수 없는 응답은 기본값으로 대신하지 않고 503이다. 둘 다 바로 던지지 않고
     * [ExpoFeatures.require]에서 던진다.
     */
    fun readFeatures(expoId: String): ExpoFeatures =
        try {
            ExpoFeatures(effectiveFeatures(expoId))
        } catch (e: ExpectedException) {
            ExpoFeatures(emptyMap(), e)
        }

    private fun effectiveFeatures(expoId: String): Map<String, Boolean> {
        val response = call(expoId) ?: throw ExpectedException(HttpStatus.NOT_FOUND, "박람회를 찾지 못 했습니다.")
        return response.features?.takeIf { it.isArray }?.associate { item ->
            val key = item.get("key")
            val enabled = item.get("effectiveEnabled")
            if (key == null || !key.isString || enabled == null || !enabled.isBoolean) throw unparsableFeatures()
            key.stringValue() to enabled.booleanValue()
        } ?: throw unparsableFeatures()
    }

    private fun unparsableFeatures(): ExpectedException {
        log.error("expo service returned unparsable features")
        return ExpoFeatures.unavailable()
    }

    private fun call(expoId: String): ExpoPeriodResDto? {
        val lookup =
            try {
                // Resilience4j는 null을 돌려주는 공급자를 받지 못하므로 "박람회 없음"도 값으로 감싼다
                circuitBreaker.executeSupplier { Lookup(fetch(expoId)) }
            } catch (e: Exception) {
                // 요청 id나 응답 본문은 남기지 않고 원인 종류만 남긴다
                log.warn("expo service call failed: {}", e.javaClass.simpleName)
                throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "박람회 정보를 가져올 수 없습니다.")
            }
        return lookup.response
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
