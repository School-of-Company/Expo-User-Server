package team.startup.expo.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.apache.kafka.common.errors.RecordTooLargeException
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import team.startup.expo.domain.participation.entity.ParticipationType
import team.startup.expo.domain.participation.entity.RegistrationEventType
import team.startup.expo.domain.participation.entity.RegistrationOutboxEvent
import team.startup.expo.domain.participation.repository.RegistrationOutboxEventRepository
import team.startup.expo.domain.participation.service.RegistrationOutboxPublisher
import team.startup.expo.global.kafka.RegistrationEventProperties
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
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
                TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true)),
            )

        fun event(
            eventId: String,
            participantId: Long,
            type: RegistrationEventType = RegistrationEventType.STANDARD_CREATED,
            participantsJson: String? = null,
        ) = RegistrationOutboxEvent(
            id = eventId.removePrefix("e").toLong(),
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

        fun rejected(): CompletableFuture<SendResult<String, String>> = CompletableFuture.failedFuture(RecordTooLargeException("too large"))

        fun reset() {
            clearMocks(repository, kafkaTemplate)
            every { repository.release(any(), any(), any(), any(), any(), any()) } returns 1
        }

        given("서로 다른 참가자의 이벤트 세 건이 있을 때") {
            `when`("두 번째 발행이 실패하면") {
                reset()
                val first = event("e1", 1L)
                val second = event("e2", 2L)
                val third = event("e3", 3L)
                every { repository.findPendingForUpdate(any(), any()) } returns listOf(first, second, third)
                every { kafkaTemplate.send(STANDARD_CREATED_TOPIC, "$EXPO:1", any()) } returns sent
                every { kafkaTemplate.send(STANDARD_CREATED_TOPIC, "$EXPO:2", any()) } returns failure()
                every { kafkaTemplate.send(STANDARD_CREATED_TOPIC, "$EXPO:3", any()) } returns sent

                val published = publisher.publishPending()

                then("실패한 건은 시도 횟수와 오류만 남기고 격리하지 않는다") {
                    published shouldBe 2
                    second.publishedAt shouldBe null
                    second.attempts shouldBe 1
                    (second.lastError != null) shouldBe true
                    second.quarantinedAt shouldBe null
                }
                then("다른 참가자의 이벤트는 막지 않고 발행한다") {
                    (first.publishedAt != null && third.publishedAt != null) shouldBe true
                    third.attempts shouldBe 0
                }
                then("점유한 기한으로 세 건의 결과를 모두 남긴다") {
                    val claimedUntil = slot<LocalDateTime>()
                    verify { repository.release(1L, capture(claimedUntil), first.publishedAt, 0, null, null) }
                    verify { repository.release(2L, claimedUntil.captured, null, 1, second.lastError, null) }
                    verify { repository.release(3L, claimedUntil.captured, third.publishedAt, 0, null, null) }
                    first.claimedUntil shouldBe claimedUntil.captured
                }
            }

            `when`("모든 발행이 실패하면") {
                reset()
                val first = event("e1", 1L).apply { attempts = 4 }
                val second = event("e2", 2L).apply { attempts = 4 }
                val third = event("e3", 3L)
                every { repository.findPendingForUpdate(any(), any()) } returns listOf(first, second, third)
                every { kafkaTemplate.send(any<String>(), any<String>(), any()) } returns failure()

                val published = publisher.publishPending()

                then("브로커 장애로 보고 두 건만 시도한 뒤 멈추고 오류를 밖으로 던지지 않는다") {
                    published shouldBe 0
                    verify(exactly = 2) { kafkaTemplate.send(any<String>(), any<String>(), any()) }
                    third.attempts shouldBe 0
                }
                then("시도 상한에 이르렀어도 발행된 건이 없으니 격리하지 않는다") {
                    first.attempts shouldBe 5
                    second.attempts shouldBe 5
                    first.quarantinedAt shouldBe null
                    second.quarantinedAt shouldBe null
                }
            }
        }

        given("한 참가자의 이벤트 두 건과 다른 참가자의 이벤트가 있을 때") {
            `when`("앞선 참가자의 첫 이벤트가 실패하면") {
                reset()
                val first = event("e1", 1L, RegistrationEventType.REGISTERED)
                val sameParticipant = event("e2", 1L)
                val other = event("e3", 2L)
                every { repository.findPendingForUpdate(any(), any()) } returns listOf(first, sameParticipant, other)
                every { kafkaTemplate.send(REGISTERED_TOPIC, "STANDARD:1", any()) } returns failure()
                every { kafkaTemplate.send(STANDARD_CREATED_TOPIC, "$EXPO:2", any()) } returns sent

                val published = publisher.publishPending()

                then("순서를 뒤집지 않으려고 같은 참가자의 뒤 이벤트는 보내지도 않고 시도 횟수도 올리지 않는다") {
                    sameParticipant.publishedAt shouldBe null
                    sameParticipant.attempts shouldBe 0
                    verify(exactly = 0) { kafkaTemplate.send(any<String>(), "$EXPO:1", any()) }
                }
                then("다른 참가자의 이벤트는 발행한다") {
                    published shouldBe 1
                    (other.publishedAt != null) shouldBe true
                }
            }
        }

        given("시도 상한 직전의 이벤트가 있을 때") {
            `when`("또 실패하고 같은 회차에 다른 이벤트는 발행되면") {
                reset()
                val failing = event("e1", 1L).apply { attempts = 4 }
                val other = event("e2", 2L)
                every { repository.findPendingForUpdate(any(), any()) } returns listOf(failing, other)
                every { kafkaTemplate.send(STANDARD_CREATED_TOPIC, "$EXPO:1", any()) } returns failure()
                every { kafkaTemplate.send(STANDARD_CREATED_TOPIC, "$EXPO:2", any()) } returns sent

                publisher.publishPending() shouldBe 1

                then("그 레코드만 계속 실패한다고 보고 지우거나 발행 완료로 표시하지 않은 채 격리한다") {
                    failing.attempts shouldBe 5
                    (failing.quarantinedAt != null) shouldBe true
                    failing.publishedAt shouldBe null
                    failing.eventId shouldBe "e1"
                }
            }
        }

        given("서로 다른 참가자의 Kafka 거부 이벤트 두 건이 맨 앞에 있고 정상 이벤트가 뒤에 있을 때") {
            `when`("여러 회차 발행하면") {
                reset()
                val firstRejected = event("e1", 1L)
                val secondRejected = event("e2", 2L)
                val normal = event("e3", 3L)
                val events = listOf(firstRejected, secondRejected, normal)
                every { repository.findPendingForUpdate(any(), any()) } answers {
                    events.filter { it.publishedAt == null && it.quarantinedAt == null }
                }
                every { kafkaTemplate.send(STANDARD_CREATED_TOPIC, "$EXPO:1", any()) } answers { rejected() }
                every { kafkaTemplate.send(STANDARD_CREATED_TOPIC, "$EXPO:2", any()) } answers { rejected() }
                every { kafkaTemplate.send(STANDARD_CREATED_TOPIC, "$EXPO:3", any()) } returns sent

                val firstRound = publisher.publishPending()
                repeat(MAX_ATTEMPTS - 1) { publisher.publishPending() }

                then("레코드 거부는 브로커 장애로 세지 않아 첫 회차에 정상 이벤트를 발행한다") {
                    firstRound shouldBe 1
                    (normal.publishedAt != null) shouldBe true
                }
                then("거부된 두 건은 시도 상한에 이른 회차에 격리되고 발행 완료로 표시하지 않는다") {
                    firstRejected.attempts shouldBe MAX_ATTEMPTS
                    secondRejected.attempts shouldBe MAX_ATTEMPTS
                    (firstRejected.quarantinedAt != null && secondRejected.quarantinedAt != null) shouldBe true
                    (firstRejected.publishedAt == null && secondRejected.publishedAt == null) shouldBe true
                }
                then("격리된 뒤에는 다시 보내지 않는다") {
                    publisher.publishPending() shouldBe 0
                    verify(exactly = MAX_ATTEMPTS) { kafkaTemplate.send(any<String>(), "$EXPO:1", any()) }
                }
            }
        }

        given("payload를 읽을 수 없는 이벤트가 있을 때") {
            `when`("발행하면") {
                reset()
                val broken = event("e1", 1L, RegistrationEventType.REGISTERED, participantsJson = "{not json")
                val other = event("e2", 2L)
                every { repository.findPendingForUpdate(any(), any()) } returns listOf(broken, other)
                every { kafkaTemplate.send(any<String>(), any<String>(), any()) } returns sent

                publisher.publishPending() shouldBe 1

                then("다시 해도 실패하므로 Kafka로 보내지 않고 첫 회차에 바로 격리한다") {
                    broken.attempts shouldBe 1
                    (broken.quarantinedAt != null) shouldBe true
                    broken.publishedAt shouldBe null
                    verify(exactly = 0) { kafkaTemplate.send(REGISTERED_TOPIC, any<String>(), any()) }
                }
                then("다른 참가자의 이벤트는 발행한다") {
                    (other.publishedAt != null) shouldBe true
                }
            }
        }

        given("한 번 실패하고 남은 이벤트가 다음 회차에 다시 조회될 때") {
            val second = event("e2", 2L)
            val third = event("e3", 3L)

            `when`("이번에는 발행이 성공하면") {
                reset()
                second.attempts = 1
                every { repository.findPendingForUpdate(any(), any()) } returns listOf(second, third)
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
                every { repository.findPendingForUpdate(any(), any()) } returns emptyList()

                then("0건이고 Kafka를 부르지 않는다") {
                    publisher.publishPending() shouldBe 0
                    verify(exactly = 0) { kafkaTemplate.send(any<String>(), any<String>(), any()) }
                    verify(exactly = 0) { repository.release(any(), any(), any(), any(), any(), any()) }
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
                every { repository.findPendingForUpdate(any(), any()) } returns listOf(registered, created)
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
        const val MAX_ATTEMPTS = 5
    }
}
