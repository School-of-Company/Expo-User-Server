package team.startup.expo.domain.participation.presentation

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import team.startup.expo.domain.participation.presentation.dto.request.SurveyAnswerSubmittedEvent
import team.startup.expo.domain.participation.presentation.dto.response.SurveyAnswerResultEvent
import team.startup.expo.domain.participation.service.SaveSurveyAnswerService
import team.startup.expo.global.kafka.MalformedSurveyAnswerEventException
import team.startup.expo.global.kafka.SurveyAnswerProperties
import team.startup.expo.global.util.QuestionSnapshot
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.TimeUnit

/**
 * Form 서비스가 발행한 설문 답변을 저장하고 결과를 되돌려 보낸다.
 *
 * 저장은 트랜잭션에서 끝내고 결과 발행은 커밋 뒤에 한다. 발행이 실패하면 예외를 던져 같은 이벤트를 다시
 * 처리하게 하고, 이미 저장한 이벤트는 저장 없이 같은 결과를 다시 발행한다. 오프셋은 발행까지 성공한 뒤에만 커밋된다.
 */
@Component
class SurveyAnswerEventConsumer(
    private val saveSurveyAnswerService: SaveSurveyAnswerService,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val jsonMapper: JsonMapper,
    private val properties: SurveyAnswerProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["\${survey-answer.submit-topic}"],
        autoStartup = "\${survey-answer.consumer.auto-startup:true}",
    )
    fun consume(record: ConsumerRecord<String, String>) {
        val event = parse(record.value())
        val result = saveSurveyAnswerService.execute(event)
        if (result == null) {
            log.info("삭제된 박람회의 설문 답변 이벤트를 건너뜀: eventId={}", event.eventId)
            return
        }
        publish(result)
        log.info("설문 답변 처리: eventId={}, status={}", result.eventId, result.status)
    }

    private fun parse(value: String?): SurveyAnswerSubmittedEvent {
        if (value.isNullOrBlank()) throw MalformedSurveyAnswerEventException("비어 있는 메시지입니다.")
        val event =
            try {
                jsonMapper.readValue(value, SurveyAnswerSubmittedEvent::class.java)
            } catch (e: Exception) {
                throw MalformedSurveyAnswerEventException("이벤트를 해석할 수 없습니다.", e)
            }
        if (event.version !in SUPPORTED_VERSIONS) {
            throw MalformedSurveyAnswerEventException("지원하지 않는 이벤트 버전입니다: ${event.version}")
        }
        if (event.eventId.isBlank() || event.eventId.length > ID_MAX_LENGTH ||
            event.surveyId.isBlank() || event.surveyId.length > ID_MAX_LENGTH ||
            event.expoId.isBlank() || event.expoId.length > ID_MAX_LENGTH
        ) {
            throw MalformedSurveyAnswerEventException("식별자가 올바르지 않습니다.")
        }
        QuestionSnapshot.errorOf(event.questions)?.let { throw MalformedSurveyAnswerEventException(it) }
        // jsonb 컬럼에 넣기 전에 걸러 낸다. 저장 단계에서 실패하면 재시도만 반복하게 된다
        try {
            jsonMapper.readTree(event.answerJson)
        } catch (e: Exception) {
            throw MalformedSurveyAnswerEventException("answerJson이 올바른 JSON이 아닙니다.", e)
        }
        return event
    }

    private fun publish(result: SurveyAnswerResultEvent) {
        kafkaTemplate
            .send(properties.resultTopic, result.eventId, jsonMapper.writeValueAsString(result))
            .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private companion object {
        // Form보다 먼저 배포해 v1과 v2를 모두 받는다. v2는 문항 스냅샷이 더해진 것이다
        val SUPPORTED_VERSIONS = setOf(1, 2)
        const val ID_MAX_LENGTH = 36
        const val SEND_TIMEOUT_SECONDS = 10L
    }
}
