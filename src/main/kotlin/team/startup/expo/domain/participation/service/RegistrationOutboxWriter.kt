package team.startup.expo.domain.participation.service

import org.springframework.stereotype.Component
import team.startup.expo.domain.participation.entity.ParticipationType
import team.startup.expo.domain.participation.entity.RegistrationEventType
import team.startup.expo.domain.participation.entity.RegistrationOutboxEvent
import team.startup.expo.domain.participation.repository.RegistrationOutboxEventRepository
import java.util.UUID

/**
 * 등록 이벤트를 아웃박스에 기록한다. 호출하는 쪽의 트랜잭션에 참여하므로 참가자 저장과 함께 확정되거나 함께
 * 되돌아간다. `eventId`는 여기서 한 번 만들고 릴레이가 몇 번을 다시 발행해도 그대로다.
 */
@Component
class RegistrationOutboxWriter(
    private val registrationOutboxEventRepository: RegistrationOutboxEventRepository,
) {
    fun registered(
        expoId: String,
        participationType: ParticipationType,
        participantId: Long,
        phoneNumber: String,
    ) = record(RegistrationEventType.REGISTERED, expoId, participationType, participantId, phoneNumber)

    fun standardCreated(
        expoId: String,
        participantId: Long,
        phoneNumber: String,
    ) = record(RegistrationEventType.STANDARD_CREATED, expoId, ParticipationType.STANDARD, participantId, phoneNumber)

    private fun record(
        eventType: RegistrationEventType,
        expoId: String,
        participationType: ParticipationType,
        participantId: Long,
        phoneNumber: String,
    ) {
        registrationOutboxEventRepository.save(
            RegistrationOutboxEvent(
                eventId = UUID.randomUUID().toString(),
                eventType = eventType,
                expoId = expoId,
                participationType = participationType,
                participantId = participantId,
                phoneNumber = phoneNumber,
            ),
        )
    }
}
