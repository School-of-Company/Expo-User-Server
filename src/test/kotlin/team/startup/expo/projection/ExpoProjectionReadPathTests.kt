package team.startup.expo.projection

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.ExpoPeriod
import team.startup.expo.global.client.expo.ExpoPeriodReader
import team.startup.expo.global.client.expo.ExpoPeriodResDto
import team.startup.expo.global.client.expo.PreregisterSessionReader
import team.startup.expo.global.client.expo.PreregisterSessionResDto
import team.startup.expo.global.client.expo.projection.ExpoProjectionProperties
import team.startup.expo.global.client.expo.projection.ExpoProjectionReader
import team.startup.expo.global.client.expo.projection.ExpoProjectionRepository
import team.startup.expo.global.client.expo.projection.ExpoProjectionRow
import java.time.LocalDate

class ExpoProjectionReadPathTests :
    BehaviorSpec({
        val expoClient = mockk<ExpoClient>()
        val repository = mockk<ExpoProjectionRepository>()

        fun readers(enabled: Boolean): Pair<ExpoPeriodReader, PreregisterSessionReader> {
            clearMocks(expoClient, repository)
            every { expoClient.getPeriod(EXPO) } returns ExpoPeriodResDto("2030-03-01", "2030-03-03")
            every { expoClient.getPreregisterSession(EXPO, SESSION) } returns PreregisterSessionResDto(SESSION, EXPO)
            val projection = ExpoProjectionReader(repository, ExpoProjectionProperties(enabled))
            val circuitBreaker = CircuitBreaker.ofDefaults("expo")
            return ExpoPeriodReader(expoClient, circuitBreaker, projection) to
                PreregisterSessionReader(expoClient, circuitBreaker, projection)
        }

        given("프로젝션이 꺼져 있으면") {
            val (periodReader, sessionReader) = readers(enabled = false)

            `when`("기간과 회차를 읽으면") {
                val period = periodReader.find(EXPO)
                val exists = sessionReader.exists(EXPO, SESSION)

                then("지금처럼 Expo를 부르고 프로젝션은 읽지 않는다") {
                    period shouldBe EXPO_PERIOD
                    exists shouldBe true
                    verify(exactly = 1) { expoClient.getPeriod(EXPO) }
                    verify(exactly = 1) { expoClient.getPreregisterSession(EXPO, SESSION) }
                    verify(exactly = 0) { repository.find(any()) }
                }
            }
        }

        given("프로젝션이 켜져 있고 박람회가 프로젝션에 있으면") {
            val (periodReader, sessionReader) = readers(enabled = true)
            every { repository.find(EXPO) } returns ExpoProjectionRow(false, PROJECTED.startedDay, PROJECTED.finishedDay, false)
            every { repository.sessionExists(EXPO, SESSION) } returns true
            every { repository.sessionExists(EXPO, OTHER_SESSION) } returns false

            `when`("기간과 회차를 읽으면") {
                val period = periodReader.find(EXPO)
                val exists = sessionReader.exists(EXPO, SESSION)
                val other = sessionReader.exists(EXPO, OTHER_SESSION)

                then("프로젝션 값을 돌려주고 Expo를 부르지 않는다") {
                    period shouldBe PROJECTED
                    exists shouldBe true
                    other shouldBe false
                    verify(exactly = 0) { expoClient.getPeriod(any()) }
                    verify(exactly = 0) { expoClient.getPreregisterSession(any(), any()) }
                }
            }
        }

        given("프로젝션이 켜져 있고 박람회가 삭제 중이면") {
            val (periodReader, sessionReader) = readers(enabled = true)
            every { repository.find(EXPO) } returns ExpoProjectionRow(false, PROJECTED.startedDay, PROJECTED.finishedDay, true)

            `when`("기간과 회차를 읽으면") {
                then("Expo처럼 기간은 돌려주고 회차는 없는 회차로 본다") {
                    periodReader.find(EXPO) shouldBe PROJECTED
                    sessionReader.exists(EXPO, SESSION) shouldBe false
                    verify(exactly = 0) { repository.sessionExists(any(), any()) }
                    verify(exactly = 0) { expoClient.getPreregisterSession(any(), any()) }
                }
            }
        }

        given("프로젝션이 켜져 있고 박람회가 tombstone으로 지워졌으면") {
            val (periodReader, sessionReader) = readers(enabled = true)
            every { repository.find(EXPO) } returns ExpoProjectionRow(true, null, null, false)

            `when`("기간과 회차를 읽으면") {
                then("없는 박람회와 없는 회차로 답하고 Expo를 부르지 않는다") {
                    periodReader.find(EXPO) shouldBe null
                    sessionReader.exists(EXPO, SESSION) shouldBe false
                    verify(exactly = 0) { expoClient.getPeriod(any()) }
                    verify(exactly = 0) { expoClient.getPreregisterSession(any(), any()) }
                }
            }
        }

        given("프로젝션이 켜져 있지만 박람회가 프로젝션에 아직 없으면") {
            val (periodReader, sessionReader) = readers(enabled = true)
            every { repository.find(EXPO) } returns null

            `when`("기간과 회차를 읽으면") {
                val period = periodReader.find(EXPO)
                val exists = sessionReader.exists(EXPO, SESSION)

                then("기존 Expo 호출로 넘어간다") {
                    period shouldBe EXPO_PERIOD
                    exists shouldBe true
                    verify(exactly = 1) { expoClient.getPeriod(EXPO) }
                    verify(exactly = 1) { expoClient.getPreregisterSession(EXPO, SESSION) }
                }
            }
        }
    }) {
    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-0000000000e1"
        const val SESSION = 7L
        const val OTHER_SESSION = 8L
        val EXPO_PERIOD = ExpoPeriod(LocalDate.of(2030, 3, 1), LocalDate.of(2030, 3, 3))
        val PROJECTED = ExpoPeriod(LocalDate.of(2031, 5, 1), LocalDate.of(2031, 5, 2))
    }
}
