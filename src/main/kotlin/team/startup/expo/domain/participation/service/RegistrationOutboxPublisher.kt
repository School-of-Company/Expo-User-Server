package team.startup.expo.domain.participation.service

import org.apache.kafka.common.InvalidRecordException
import org.apache.kafka.common.errors.RecordBatchTooLargeException
import org.apache.kafka.common.errors.RecordTooLargeException
import org.slf4j.LoggerFactory
import org.springframework.core.NestedExceptionUtils
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.entity.ParticipationType
import team.startup.expo.domain.participation.entity.RegistrationEventType
import team.startup.expo.domain.participation.entity.RegistrationOutboxEvent
import team.startup.expo.domain.participation.presentation.dto.response.RegistrationCompletedEvent
import team.startup.expo.domain.participation.presentation.dto.response.StandardParticipantCreatedEvent
import team.startup.expo.domain.participation.repository.RegistrationOutboxEventRepository
import team.startup.expo.global.kafka.RegistrationEventProperties
import tools.jackson.core.JacksonException
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 아직 발행하지 않은 아웃박스 이벤트를 Kafka로 보낸다. 오래된 것부터 한 건씩 보내고, 보낸 건은 바로 발행 완료로 표시한다.
 *
 * 순서는 참가자(`participationType`, `participantId`) 단위로 지킨다. 한 건이 실패하면 시도 횟수를 올리고 이번 회차에서
 * 그 참가자의 뒤 이벤트만 건너뛰며, 다른 참가자의 이벤트는 계속 보낸다. 레코드 거부가 아닌 실패가 연달아
 * [MAX_FAILURES_IN_ROW]건이면 브로커 장애로 보고 회차를 멈춘다(건마다 전송 시간 제한까지 기다리지 않도록).
 *
 * 지속 실패는 격리한다([RegistrationOutboxEvent.quarantinedAt]). payload를 읽을 수 없으면 바로, Kafka가 레코드 자체를
 * 거부하면([isRecordRejected]) 시도가 [MAX_ATTEMPTS]번에 이른 그 자리에서 격리하고 다음 이벤트로 넘어간다. 그 밖의 실패는
 * 시도가 [MAX_ATTEMPTS]번에 이르고 같은 회차에 다른 이벤트가 발행됐을 때(브로커는 살아 있고 그 레코드만 실패할 때) 격리한다.
 * 그래서 브로커 장애 동안에는 격리하지 않는다. 격리한 이벤트는 지우거나 발행 완료로 표시하지 않고, 그 참가자의 뒤 이벤트도
 * 계속 멈춘다. `quarantined_at`을 비우면 같은 `eventId`로 다시 발행한다(V17 마이그레이션 참고).
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
        var failuresInRow = 0
        val blocked = mutableSetOf<Pair<ParticipationType, Long>>()
        val failed = mutableListOf<RegistrationOutboxEvent>()
        for (event in registrationOutboxEventRepository.findPendingForUpdate(BATCH_SIZE)) {
            val participant = event.participationType to event.participantId
            if (participant in blocked) continue
            try {
                send(event)
            } catch (exception: JacksonException) {
                blocked += participant
                recordFailure(event, exception)
                quarantine(event)
                continue
            } catch (exception: Exception) {
                blocked += participant
                recordFailure(event, exception)
                // 레코드 거부는 브로커 장애가 아니므로 연속 실패로 세지 않는다. 세면 거부 두 건이 맨 앞을 계속 막는다
                if (isRecordRejected(exception)) {
                    if (event.attempts >= MAX_ATTEMPTS) quarantine(event)
                    continue
                }
                failed += event
                if (++failuresInRow >= MAX_FAILURES_IN_ROW) break
                continue
            }
            failuresInRow = 0
            event.publishedAt = LocalDateTime.now()
            published += 1
        }
        if (published > 0) failed.filter { it.attempts >= MAX_ATTEMPTS }.forEach(::quarantine)
        return published
    }

    private fun recordFailure(
        event: RegistrationOutboxEvent,
        exception: Exception,
    ) {
        event.attempts += 1
        // Kafka 오류는 ExecutionException·KafkaProducerException에 감싸여 오므로 원인을 남긴다
        event.lastError = NestedExceptionUtils.getMostSpecificCause(exception).toString().take(MAX_ERROR_LENGTH)
        log.warn("등록 이벤트 발행 실패: eventId={}, attempts={}", event.eventId, event.attempts, exception)
    }

    /** 브로커 상태와 무관하게 그 레코드만 계속 실패하는 오류. */
    private fun isRecordRejected(exception: Exception): Boolean =
        when (NestedExceptionUtils.getMostSpecificCause(exception)) {
            is RecordTooLargeException, is RecordBatchTooLargeException, is InvalidRecordException -> true
            else -> false
        }

    private fun quarantine(event: RegistrationOutboxEvent) {
        event.quarantinedAt = LocalDateTime.now()
        log.error(
            "등록 이벤트 격리: eventId={}, participant={}:{}, attempts={}",
            event.eventId,
            event.participationType,
            event.participantId,
            event.attempts,
        )
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
        const val MAX_ATTEMPTS = 5
        const val MAX_FAILURES_IN_ROW = 2
        const val MAX_ERROR_LENGTH = 1000
    }
}
