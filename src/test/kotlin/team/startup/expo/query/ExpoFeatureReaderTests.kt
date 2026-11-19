package team.startup.expo.query

import feign.FeignException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.ExpoFeature
import team.startup.expo.global.client.expo.ExpoPeriodReader
import team.startup.expo.global.client.expo.ExpoPeriodResDto
import team.startup.expo.global.client.expo.projection.ExpoProjectionReader
import team.startup.expo.global.exception.ExpectedException
import tools.jackson.databind.json.JsonMapper

class ExpoFeatureReaderTests :
    BehaviorSpec({
        val json = JsonMapper.builder().build()
        val expoClient = mockk<ExpoClient>()
        val projection = mockk<ExpoProjectionReader>()
        val reader = ExpoPeriodReader(expoClient, CircuitBreaker.ofDefaults("expo"), projection)

        fun respond(features: String?) {
            every { expoClient.getPeriod(EXPO) } returns ExpoPeriodResDto("2030-01-01", "2030-01-02", features?.let(json::readTree))
        }

        given("Expo가 features 배열을 주면") {
            `when`("실효 값을 읽으면") {
                respond(
                    """[{"key":"PRE_REGISTRATION","configuredEnabled":true,"effectiveEnabled":true},""" +
                        """{"key":"COMPANION_REGISTRATION","configuredEnabled":true,"effectiveEnabled":false}]""",
                )
                val features = reader.readFeatures(EXPO)

                then("켜진 키는 통과, 꺼진 키는 409 FEATURE_DISABLED, 없는 키는 503이다") {
                    features.require(ExpoFeature.PRE_REGISTRATION)
                    val off = shouldThrow<ExpectedException> { features.require(ExpoFeature.COMPANION_REGISTRATION) }
                    off.status shouldBe HttpStatus.CONFLICT
                    off.code shouldBe "FEATURE_DISABLED"
                    shouldThrow<ExpectedException> { features.require(ExpoFeature.FIELD_REGISTRATION) }.status shouldBe
                        HttpStatus.SERVICE_UNAVAILABLE
                }

                then("프로젝션은 읽지 않는다") {
                    verify(exactly = 0) { projection.findPeriod(any()) }
                }
            }
        }

        given("해석할 수 없는 features") {
            listOf(
                null,
                """{"PRE_REGISTRATION":true}""",
                """[{"key":"PRE_REGISTRATION","effectiveEnabled":"true"}]""",
                """[{"key":"PRE_REGISTRATION"}]""",
                """[{"effectiveEnabled":true}]""",
            ).forEach { features ->
                `when`("응답이 $features 이면") {
                    then("허용으로 보지 않고 503이다") {
                        respond(features)
                        val features = reader.readFeatures(EXPO)
                        shouldThrow<ExpectedException> { features.require(ExpoFeature.PRE_REGISTRATION) }.status shouldBe
                            HttpStatus.SERVICE_UNAVAILABLE
                    }
                }
            }
        }

        given("Expo 호출이 실패하거나 박람회가 없으면") {
            `when`("기능 설정을 읽어도") {
                then("바로 던지지 않고 require에서 503, 없는 박람회는 404를 던진다") {
                    every { expoClient.getPeriod(EXPO) } throws RuntimeException("down")
                    val down = reader.readFeatures(EXPO)
                    shouldThrow<ExpectedException> { down.require(ExpoFeature.PRE_REGISTRATION) }.status shouldBe
                        HttpStatus.SERVICE_UNAVAILABLE

                    every { expoClient.getPeriod(EXPO) } throws mockk<FeignException.NotFound>(relaxed = true)
                    val missing = reader.readFeatures(EXPO)
                    shouldThrow<ExpectedException> { missing.require(ExpoFeature.PRE_REGISTRATION) }.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }
    }) {
    companion object {
        const val EXPO = "expo-1"
    }
}
