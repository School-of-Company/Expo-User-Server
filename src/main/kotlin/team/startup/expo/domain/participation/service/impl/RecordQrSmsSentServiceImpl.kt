package team.startup.expo.domain.participation.service.impl

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.entity.ParticipationType
import team.startup.expo.domain.participation.presentation.dto.request.QrSmsSentEvent
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.ExpoDeletionGuard
import team.startup.expo.domain.participation.service.RecordQrSmsSentService
import team.startup.expo.domain.participation.service.SmsTryCounter

/**
 * QR 문자 발송에 성공했다는 이벤트로 일반 참가자의 `smsTryTime`을 올린다(`sms-try` HTTP 호출을 대체한다).
 * 이벤트는 중복되거나 재전달될 수 있으므로 같은 `eventId`는 한 번만 올린다([SmsTryCounter]).
 *
 * 다시 읽어도 결과가 같은 이벤트는 재시도하지 않고 건너뛴다. 연수자는 횟수를 세지 않고, 삭제 중이거나 삭제된 박람회와
 * 그 박람회에 없는 참가자, 이미 다른 참가자에 쓴 `eventId`도 마찬가지다. 건너뛴 이유는 로그에 남기되 `eventId`만 적고
 * 전화번호는 이벤트에 없다. 일시적 장애는 예외로 전파해 컨슈머가 재시도하게 한다.
 *
 * 박람회 삭제와는 같은 박람회 단위로 직렬화한다([ExpoDeletionGuard]). 삭제 기록이 있으면 아무것도 쓰지 않는다.
 */
@Service
class RecordQrSmsSentServiceImpl(
    private val standardParticipantRepository: StandardParticipantRepository,
    private val smsTryCounter: SmsTryCounter,
    private val expoDeletionGuard: ExpoDeletionGuard,
) : RecordQrSmsSentService {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun execute(event: QrSmsSentEvent) {
        if (event.participationType != ParticipationType.STANDARD) {
            log.info("연수자의 발송 완료 이벤트는 횟수를 세지 않아 건너뜀: eventId={}", event.eventId)
            return
        }
        if (expoDeletionGuard.isDeleted(event.expoId)) {
            log.info("삭제된 박람회의 발송 완료 이벤트를 건너뜀: eventId={}", event.eventId)
            return
        }
        if (!standardParticipantRepository.existsByIdAndExpoId(event.id, event.expoId)) {
            log.warn("박람회에 없는 참가자의 발송 완료 이벤트를 건너뜀: eventId={}", event.eventId)
            return
        }
        when (smsTryCounter.increaseOnce(event.eventId, event.id)) {
            SmsTryCounter.Outcome.INCREASED -> log.info("QR 문자 발송 횟수 증가: eventId={}", event.eventId)
            SmsTryCounter.Outcome.DUPLICATE -> log.info("이미 처리한 발송 완료 이벤트: eventId={}", event.eventId)
            SmsTryCounter.Outcome.CONFLICT -> log.error("이미 다른 참가자에 쓴 eventId라 건너뜀: eventId={}", event.eventId)
        }
    }
}
