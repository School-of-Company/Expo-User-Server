package team.startup.expo.global.client.expo.projection

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import team.startup.expo.global.client.expo.ExpoPeriod
import team.startup.expo.global.kafka.MalformedExpoChangedEventException
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Expo가 발행한 박람회 변경 이벤트를 소비해 로컬 프로젝션을 갱신한다([ExpoProjectionUpdater]).
 *
 * 해석할 수 없는 이벤트는 다시 읽어도 같으므로 재시도 없이 dead letter로 보낸다. 처리 중 일시적 장애는 예외로 전파해
 * 오프셋을 커밋하지 않고 다시 처리하게 한다. 중복·순서 역전은 `version`으로 걸러진다.
 */
@Component
class ExpoChangedEventConsumer(
    private val expoProjectionUpdater: ExpoProjectionUpdater,
    private val jsonMapper: JsonMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["\${expo-changed.topic}"],
        groupId = "\${expo-changed.group-id}",
        autoStartup = "\${expo-changed.consumer.auto-startup:false}",
    )
    fun consume(record: ConsumerRecord<String, String>) {
        val change = parse(record.value())
        if (expoProjectionUpdater.apply(change)) {
            log.info("박람회 프로젝션 반영: eventId={}, version={}, deleted={}", change.eventId, change.version, change.deleted)
        } else {
            log.info("이미 반영한 버전 이하의 박람회 변경 이벤트를 건너뜀: eventId={}, version={}", change.eventId, change.version)
        }
    }

    fun parse(value: String?): ExpoProjectionChange {
        if (value.isNullOrBlank()) throw MalformedExpoChangedEventException("비어 있는 메시지입니다.")
        val event =
            try {
                jsonMapper.readValue(value, ExpoChangedEvent::class.java)
            } catch (e: Exception) {
                throw MalformedExpoChangedEventException("이벤트를 해석할 수 없습니다.", e)
            }
        if (event.schemaVersion != SCHEMA_VERSION) throw MalformedExpoChangedEventException("지원하지 않는 schemaVersion입니다.")
        val eventId = event.eventId.validId() ?: throw MalformedExpoChangedEventException("eventId가 올바르지 않습니다.")
        val expoId = event.expoId.validId() ?: throw MalformedExpoChangedEventException("expoId가 올바르지 않습니다.")
        val version = event.version?.takeIf { it > 0 } ?: throw MalformedExpoChangedEventException("version이 올바르지 않습니다.")
        if (event.deleted == true) {
            return ExpoProjectionChange(expoId, eventId, version, deleted = true, period = null, deleting = false, sessionIds = emptySet())
        }
        val expo = event.expo ?: throw MalformedExpoChangedEventException("박람회 상태가 없습니다.")
        val sessionIds =
            event.preregisterSessions.orEmpty().map {
                it.id?.takeIf { id -> id > 0 } ?: throw MalformedExpoChangedEventException("회차 id가 올바르지 않습니다.")
            }
        return ExpoProjectionChange(
            expoId = expoId,
            eventId = eventId,
            version = version,
            deleted = false,
            period = ExpoPeriod(day(expo.startedDay), day(expo.finishedDay)),
            deleting = expo.deleting == true,
            sessionIds = sessionIds.toSet(),
        )
    }

    private fun String?.validId(): String? = this?.takeIf { it.isNotBlank() && it.length <= ID_MAX_LENGTH }

    private fun day(value: String?): LocalDate =
        try {
            LocalDate.parse(value ?: throw MalformedExpoChangedEventException("박람회 기간이 없습니다."))
        } catch (e: DateTimeParseException) {
            throw MalformedExpoChangedEventException("박람회 기간을 해석할 수 없습니다.", e)
        }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val ID_MAX_LENGTH = 36
    }
}
