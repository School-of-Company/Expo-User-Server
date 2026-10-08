package team.startup.expo.domain.participation.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.entity.ParticipationType
import team.startup.expo.domain.participation.presentation.dto.request.CreateStandardParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.response.CreateStandardParticipantResDto
import team.startup.expo.domain.participation.service.CreateStandardParticipantService
import team.startup.expo.domain.participation.service.RegisterStandardParticipantService
import team.startup.expo.domain.participation.service.Registration
import team.startup.expo.domain.participation.service.RegistrationIdempotency
import team.startup.expo.domain.participation.service.RegistrationOutboxWriter

/**
 * 일반 참가자 등록에 요청 멱등(`requestId`)과 아웃박스 이벤트를 더한다. 저장은 [CreateStandardParticipantService]가
 * 하고, 요청 기록과 이벤트는 그 저장과 같은 트랜잭션에서 확정된다.
 *
 * 등록이 성공하면(새로 만들었거나 QR 재발송이 허용된 기존 참가자) 등록 완료 이벤트를 만들고, 새로 만든 경우에는
 * Expo의 신청 인원 집계를 재처리할 이벤트도 만든다. 같은 `requestId`의 재시도는 이벤트를 새로 만들지 않는다.
 */
@Service
class RegisterStandardParticipantServiceImpl(
    private val createStandardParticipantService: CreateStandardParticipantService,
    private val registrationIdempotency: RegistrationIdempotency,
    private val registrationOutboxWriter: RegistrationOutboxWriter,
) : RegisterStandardParticipantService {
    @Transactional
    override fun execute(reqDto: CreateStandardParticipantReqDto): CreateStandardParticipantResDto {
        val registration =
            registrationIdempotency.execute(
                requestId = reqDto.requestId,
                participationType = ParticipationType.STANDARD,
                expoId = reqDto.expoId,
                body = reqDto.copy(requestId = null),
            ) {
                val created = createStandardParticipantService.execute(reqDto)
                registrationOutboxWriter.registered(
                    reqDto.expoId,
                    ParticipationType.STANDARD,
                    created.participantId,
                    created.phoneNumber,
                    created.participants,
                )
                // 새로 만든 참가자마다 집계 이벤트를 만든다. 번호는 대표자만 있다
                created.createdIds.forEach {
                    val phoneNumber = if (it == created.participantId) created.phoneNumber else null
                    registrationOutboxWriter.standardCreated(reqDto.expoId, it, phoneNumber)
                }
                Registration(created.participantId, created.phoneNumber, created.created, created.participants.map { it.id })
            }
        return CreateStandardParticipantResDto(registration.id, registration.phoneNumber, registration.created, registration.participantIds)
    }
}
