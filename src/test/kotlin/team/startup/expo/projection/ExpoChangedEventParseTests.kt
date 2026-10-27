package team.startup.expo.projection

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import team.startup.expo.global.client.expo.ExpoPeriod
import team.startup.expo.global.client.expo.projection.ExpoChangedEventConsumer
import team.startup.expo.global.kafka.MalformedExpoChangedEventException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.LocalDate

class ExpoChangedEventParseTests :
    BehaviorSpec({
        val consumer = ExpoChangedEventConsumer(mockk(), JsonMapper.builder().addModule(KotlinModule.Builder().build()).build())

        given("박람회 전체 상태 메시지는") {
            `when`("모르는 필드가 섞여 있어도") {
                val change = consumer.parse(ExpoChangedPayloads.state(EVENT, 10, sessions = listOf(7, 9), extra = true))

                then("이 서비스가 읽는 값만 꺼낸다") {
                    change.expoId shouldBe ExpoChangedPayloads.EXPO
                    change.eventId shouldBe EVENT
                    change.version shouldBe 10L
                    change.deleted shouldBe false
                    change.period shouldBe ExpoPeriod(LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 2))
                    change.deleting shouldBe false
                    change.sessionIds shouldBe setOf(7L, 9L)
                }
            }
        }

        given("tombstone은") {
            `when`("상태 없이 오면") {
                val change = consumer.parse(ExpoChangedPayloads.tombstone(EVENT, 11))

                then("기간과 회차가 없는 삭제로 읽는다") {
                    change.deleted shouldBe true
                    change.period shouldBe null
                    change.sessionIds shouldBe emptySet()
                }
            }
        }

        given("해석할 수 없는 메시지는") {
            val broken =
                listOf(
                    "",
                    "not-json",
                    ExpoChangedPayloads.state(EVENT, 1).replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
                    ExpoChangedPayloads.state(EVENT, 0),
                    ExpoChangedPayloads.state("", 1),
                    ExpoChangedPayloads.state(EVENT, 1).replace("2030-01-01", "2030/01/01"),
                    ExpoChangedPayloads.state(EVENT, 1, sessions = listOf(0)),
                    """{"schemaVersion":1,"eventId":"$EVENT","version":1,"expoId":"${ExpoChangedPayloads.EXPO}","deleted":false}""",
                )

            `when`("파싱하면") {
                then("재시도하지 않는 예외로 dead letter에 보낸다") {
                    broken.forEach { shouldThrow<MalformedExpoChangedEventException> { consumer.parse(it) } }
                }
            }
        }
    }) {
    private companion object {
        const val EVENT = "6f1c3a0e-8a43-4d4e-9a55-0d6f3e1b2c11"
    }
}
