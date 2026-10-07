package team.startup.expo.domain.participation.presentation

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import team.startup.expo.domain.participation.presentation.dto.request.QrSmsSentEvent
import team.startup.expo.domain.participation.service.RecordQrSmsSentService
import team.startup.expo.global.kafka.MalformedQrSmsSentEventException
import tools.jackson.databind.json.JsonMapper

/**
 * Notification 서비스가 발행한 QR 문자 발송 완료 이벤트를 소비해 일반 참가자의 발송 횟수를 올린다.
 *
 * 해석할 수 없는 이벤트는 다시 읽어도 같으므로 재시도 없이 dead letter로 보낸다. 처리 중 일시적 장애는 예외로 전파해
 * 오프셋을 커밋하지 않고 다시 처리하게 하며, 같은 `eventId`는 서비스가 한 번만 반영한다.
 */
@Component
class QrSmsSentEventConsumer(
    private val recordQrSmsSentService: RecordQrSmsSentService,
    private val jsonMapper: JsonMapper,
) {
    @KafkaListener(
        topics = ["\${qr-sms-sent.topic}"],
        groupId = "\${qr-sms-sent.group-id}",
        autoStartup = "\${qr-sms-sent.consumer.auto-startup:true}",
    )
    fun consume(record: ConsumerRecord<String, String>) {
        recordQrSmsSentService.execute(parse(record.value()))
    }

    private fun parse(value: String?): QrSmsSentEvent {
        if (value.isNullOrBlank()) throw MalformedQrSmsSentEventException("비어 있는 메시지입니다.")
        val event =
            try {
                jsonMapper.readValue(value, QrSmsSentEvent::class.java)
            } catch (e: Exception) {
                throw MalformedQrSmsSentEventException("이벤트를 해석할 수 없습니다.", e)
            }
        if (event.eventId.isBlank() || event.eventId.length > ID_MAX_LENGTH ||
            event.expoId.isBlank() || event.expoId.length > ID_MAX_LENGTH ||
            event.id <= 0
        ) {
            throw MalformedQrSmsSentEventException("식별자가 올바르지 않습니다.")
        }
        return event
    }

    private companion object {
        const val ID_MAX_LENGTH = 36
    }
}
