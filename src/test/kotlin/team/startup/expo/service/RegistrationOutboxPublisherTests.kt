package team.startup.expo.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import team.startup.expo.domain.participation.entity.ParticipationType
import team.startup.expo.domain.participation.entity.RegistrationEventType
import team.startup.expo.domain.participation.entity.RegistrationOutboxEvent
import team.startup.expo.domain.participation.repository.RegistrationOutboxEventRepository
import team.startup.expo.domain.participation.service.RegistrationOutboxPublisher
import team.startup.expo.global.kafka.RegistrationEventProperties
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.CompletableFuture

class RegistrationOutboxPublisherTests :
    BehaviorSpec({
        val repository = mockk<RegistrationOutboxEventRepository>()
        val kafkaTemplate = mockk<KafkaTemplate<String, String>>()
        val jsonMapper = JsonMapper.builder().build()
        val publisher =
            RegistrationOutboxPublisher(
                repository,
                kafkaTemplate,
                jsonMapper,
                RegistrationEventProperties(registeredTopic = REGISTERED_TOPIC, standardCreatedTopic = STANDARD_CREATED_TOPIC),
            )

        fun event(
            eventId: String,
            participantId: Long,
            type: RegistrationEventType = RegistrationEventType.STANDARD_CREATED,
            participantsJson: String? = null,
        ) = RegistrationOutboxEvent(
            eventId = eventId,
            eventType = type,
            expoId = EXPO,
            participationType = ParticipationType.STANDARD,
            participantId = participantId,
            phoneNumber = if (type == RegistrationEventType.REGISTERED) PHONE else null,
            participantsJson = participantsJson,
        )

        val sent = CompletableFuture.completedFuture(mockk<SendResult<String, String>>())

        fun failure(): CompletableFuture<SendResult<String, String>> = CompletableFuture.failedFuture(IllegalStateException("broker down"))

        fun reset() {
            clearMocks(repository, kafkaTemplate)
        }

        given("발행하지 않은 이벤트 세 건이 있을 때") {
            `when`("두 번째 발행이 실패하면") {
                reset()
                val first = event("e1", 1L)
                val second = event("e2", 2L)
                val third = event("e3", 3L)
                every { repository.findPendingForUpdate(any()) } returns listOf(first, second, third)
                every { kafkaTemplate.send(STANDARD_CREATED_TOPIC, "$EXPO:1", any()) } returns sent
                every { kafkaTemplate.send(STANDARD_CREATED_TOPIC, "$EXPO:2", any()) } returns failure()

                val published = publisher.publishPending()

                then("첫 건만 발행 완료로 표시하고 실패한 건은 시도 횟수만 올린다") {
                    published shouldBe 1
                    (first.publishedAt != null) shouldBe true
                    second.publishedAt shouldBe null
                    second.attempts shouldBe 1
                }
                then("순서를 건너뛰지 않으려고 세 번째 건은 보내지도 않고 시도 횟수도 올리지 않는다") {
                    third.publishedAt shouldBe null
                    third.attempts shouldBe 0
                    verify(exactly = 0) { kafkaTemplate.send(any<String>(), "$EXPO:3", any()) }
                }
            }

            `when`("첫 번째 발행부터 실패하면") {
                reset()
                val first = event("e1", 1L)
                val second = event("e2", 2L)
                every { repository.findPendingForUpdate(any()) } returns listOf(first, second)
                every { kafkaTemplate.send(any<String>(), any<String>(), any()) } returns failure()

                val published = publisher.publishPending()

                then("아무것도 발행하지 않고 오류를 밖으로 던지지 않는다") {
                    published shouldBe 0
                    first.attempts shouldBe 1
                    first.publishedAt shouldBe null
                    second.attempts shouldBe 0
                    verify(exactly = 1) { kafkaTemplate.send(any<String>(), any<String>(), any()) }
                }
            }
        }

        given("한 번 실패하고 남은 이벤트가 다음 회차에 다시 조회될 때") {
            val second = event("e2", 2L)
            val third = event("e3", 3L)

            `when`("이번에는 발행이 성공하면") {
                reset()
                second.attempts = 1
                every { repository.findPendingForUpdate(any()) } returns listOf(second, third)
                every { kafkaTemplate.send(any<String>(), any<String>(), any()) } returns sent

                val published = publisher.publishPending()

                then("남은 건을 오래된 순서대로 모두 발행하고 시도 횟수는 그대로 둔다") {
                    published shouldBe 2
                    (second.publishedAt != null && third.publishedAt != null) shouldBe true
                    second.attempts shouldBe 1
                    verifyOrder {
                        kafkaTemplate.send(any<String>(), "$EXPO:2", any())
                        kafkaTemplate.send(any<String>(), "$EXPO:3", any())
                    }
                }
            }
        }

        given("발행할 이벤트가 없을 때") {
            `when`("발행하면") {
                reset()
                every { repository.findPendingForUpdate(any()) } returns emptyList()

                then("0건이고 Kafka를 부르지 않는다") {
                    publisher.publishPending() shouldBe 0
                    verify(exactly = 0) { kafkaTemplate.send(any<String>(), any<String>(), any()) }
                }
            }
        }

        given("등록 완료 이벤트와 신규 저장 이벤트가 있을 때") {
            `when`("발행하면") {
                reset()
                val registered =
                    event("e1", 10L, RegistrationEventType.REGISTERED, participantsJson = """[{"id":10,"code":"abcdefghijklmnopqrstuv"}]""")
                val created = event("e2", 11L)
                val messages = mutableMapOf<String, String>()
                every { repository.findPendingForUpdate(any()) } returns listOf(registered, created)
                every { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) } answers {
                    messages["${firstArg<String>()}|${secondArg<String>()}"] = thirdArg()
                    sent
                }

                publisher.publishPending() shouldBe 2

                then("각 이벤트를 자기 토픽과 키로 보내고 대표자 id와 참가자 목록을 싣는다") {
                    val registeredMessage = jsonMapper.readTree(messages.getValue("$REGISTERED_TOPIC|STANDARD:10"))
                    registeredMessage.get("eventId").asString() shouldBe "e1"
                    registeredMessage.get("representativeId").asLong() shouldBe 10L
                    registeredMessage.get("participants").size() shouldBe 1
                    registeredMessage.get("phoneNumber").asString() shouldBe PHONE

                    val createdMessage = jsonMapper.readTree(messages.getValue("$STANDARD_CREATED_TOPIC|$EXPO:11"))
                    createdMessage.get("eventId").asString() shouldBe "e2"
                    createdMessage.get("participantId").asLong() shouldBe 11L
                }
            }
        }
    }) {
    private companion object {
        const val REGISTERED_TOPIC = "user.participant.registered"
        const val STANDARD_CREATED_TOPIC = "user.standard-participant.created"
        const val EXPO = "0199aaaa-0000-7000-8000-000000000e04"
        const val PHONE = "01012345678"
    }
}
