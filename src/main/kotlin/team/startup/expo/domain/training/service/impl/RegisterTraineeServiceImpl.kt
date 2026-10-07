package team.startup.expo.domain.training.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.entity.ParticipationType
import team.startup.expo.domain.participation.service.Registration
import team.startup.expo.domain.participation.service.RegistrationIdempotency
import team.startup.expo.domain.participation.service.RegistrationOutboxWriter
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.domain.training.presentation.dto.request.CreateTraineeReqDto
import team.startup.expo.domain.training.presentation.dto.response.CreateTraineeResDto
import team.startup.expo.domain.training.service.CreateTraineeService
import team.startup.expo.domain.training.service.RegisterTraineeService

/**
 * 연수자 등록에 요청 멱등(`requestId`)과 아웃박스 이벤트를 더한다. 저장은 [CreateTraineeService]가 하고, 요청 기록과
 * 이벤트는 그 저장과 같은 트랜잭션에서 확정된다.
 *
 * 등록 완료 이벤트는 현장 등록(`FIELD`)에만 만든다. 사전 등록(`PRE`)은 QR 문자를 보내지 않는다.
 * 같은 `requestId`의 재시도는 이벤트를 새로 만들지 않는다.
 */
@Service
class RegisterTraineeServiceImpl(
    private val createTraineeService: CreateTraineeService,
    private val registrationIdempotency: RegistrationIdempotency,
    private val registrationOutboxWriter: RegistrationOutboxWriter,
) : RegisterTraineeService {
    @Transactional
    override fun execute(reqDto: CreateTraineeReqDto): CreateTraineeResDto {
        val registration =
            registrationIdempotency.execute(
                requestId = reqDto.requestId,
                participationType = ParticipationType.TRAINEE,
                expoId = reqDto.expoId,
                body = reqDto.copy(requestId = null),
            ) {
                val created = createTraineeService.execute(reqDto)
                if (reqDto.applicationType == ApplicationType.FIELD) {
                    registrationOutboxWriter.registered(reqDto.expoId, ParticipationType.TRAINEE, created.traineeId, created.phoneNumber)
                }
                Registration(created.traineeId, created.phoneNumber, created = true)
            }
        return CreateTraineeResDto(registration.id, registration.phoneNumber)
    }
}
