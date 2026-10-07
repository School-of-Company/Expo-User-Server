package team.startup.expo.domain.participation.service

import org.springframework.stereotype.Component
import team.startup.expo.domain.participation.repository.SmsTryEventRepository
import team.startup.expo.domain.participation.repository.StandardParticipantRepository

/**
 * 같은 `eventId`는 일반 참가자의 `smsTryTime`을 한 번만 올린다. `sms-try` HTTP 호출과 발송 완료 이벤트 소비가 같은
 * 규칙을 쓰도록 한곳에 둔다. 횟수가 2에 닿으면 재신청이 막히므로 재시도나 중복 전달이 횟수를 두 번 올리면 안 된다.
 * 호출하는 쪽이 트랜잭션 안에 있어야 한다.
 */
@Component
class SmsTryCounter(
    private val smsTryEventRepository: SmsTryEventRepository,
    private val standardParticipantRepository: StandardParticipantRepository,
) {
    enum class Outcome {
        /** 처음 보는 `eventId`라 횟수를 올렸다. */
        INCREASED,

        /** 같은 참가자에 이미 처리한 `eventId`라 아무것도 하지 않았다. */
        DUPLICATE,

        /** 이미 다른 참가자에 쓴 `eventId`다. 호출자의 실수이므로 횟수를 올리지 않았다. */
        CONFLICT,
    }

    fun increaseOnce(
        eventId: String,
        participantId: Long,
    ): Outcome {
        if (smsTryEventRepository.insertIfAbsent(eventId, participantId) == 0) {
            val recordedParticipantId = smsTryEventRepository.findById(eventId).orElse(null)?.participantId
            return if (recordedParticipantId == participantId) Outcome.DUPLICATE else Outcome.CONFLICT
        }
        standardParticipantRepository.increaseSmsTryTime(participantId)
        return Outcome.INCREASED
    }
}
