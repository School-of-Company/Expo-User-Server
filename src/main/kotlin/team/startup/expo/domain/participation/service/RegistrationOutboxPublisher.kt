package team.startup.expo.domain.participation.service

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.entity.RegistrationEventType
import team.startup.expo.domain.participation.entity.RegistrationOutboxEvent
import team.startup.expo.domain.participation.presentation.dto.response.RegistrationCompletedEvent
import team.startup.expo.domain.participation.presentation.dto.response.StandardParticipantCreatedEvent
import team.startup.expo.domain.participation.repository.RegistrationOutboxEventRepository
import team.startup.expo.global.kafka.RegistrationEventProperties
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 아직 발행하지 않은 아웃박스 이벤트를 Kafka로 보낸다. 오래된 것부터 한 건씩 보내고, 보낸 건은 바로 발행 완료로
 * 표시한다. 한 건이 실패하면 그 건의 시도 횟수만 올리고 이번 회차를 멈춘다(순서를 건너뛰지 않고 다음 회차에 다시 시도한다).
 *
 * 발행 성공 응답을 받은 뒤 커밋 전에 죽으면 같은 이벤트가 다시 나간다. 같은 `eventId`로 나가므로 소비자는
 * `eventId`로 중복을 걸러야 한다. 박람회 삭제는 발행하지 않은 이벤트만 지우므로, 이미 나간 이벤트를 받은 소비자는 처리 전에
 * 참가자가 남아 있는지 확인해야 한다. 조회는 `SKIP LOCKED`라 인스턴스가 여럿이어도 같은 이벤트를 동시에 보내지 않는다.
 */
@Component
class RegistrationOutboxPublisher(
    private val registrationOutboxEventRepository: RegistrationOutboxEventRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val jsonMapper: JsonMapper,
    private val properties: RegistrationEventProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 이번 회차에 발행한 건수를 돌려준다. */
    @Transactional
    fun publishPending(): Int {
        var published = 0
        for (event in registrationOutboxEventRepository.findPendingForUpdate(BATCH_SIZE)) {
            try {
                send(event)
            } catch (exception: Exception) {
                event.attempts += 1
                log.warn("등록 이벤트 발행 실패: eventId={}, attempts={}", event.eventId, event.attempts, exception)
                break
            }
            event.publishedAt = LocalDateTime.now()
            published += 1
        }
        return published
    }

    private fun send(event: RegistrationOutboxEvent) {
        when (event.eventType) {
            RegistrationEventType.REGISTERED -> {
                val participants =
                    event.participantsJson?.let { jsonMapper.readValue(it, object : TypeReference<List<RegisteredParticipant>>() {}) }
                val message =
                    RegistrationCompletedEvent(
                        eventId = event.eventId,
                        expoId = event.expoId,
                        participationType = event.participationType,
                        id = event.participantId,
                        phoneNumber = event.phoneNumber,
                        representativeId = if (participants != null) event.participantId else null,
                        participants = participants,
                    )
                publish(properties.registeredTopic, "${event.participationType}:${event.participantId}", message)
            }

            RegistrationEventType.STANDARD_CREATED -> {
                val message = StandardParticipantCreatedEvent(event.eventId, event.expoId, event.participantId)
                publish(properties.standardCreatedTopic, "${event.expoId}:${event.participantId}", message)
            }
        }
    }

    private fun publish(
        topic: String,
        key: String,
        message: Any,
    ) {
        kafkaTemplate.send(topic, key, jsonMapper.writeValueAsString(message)).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private companion object {
        const val BATCH_SIZE = 50
        const val SEND_TIMEOUT_SECONDS = 10L
    }
}
