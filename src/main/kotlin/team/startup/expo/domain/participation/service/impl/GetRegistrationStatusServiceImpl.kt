package team.startup.expo.domain.participation.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.entity.RegistrationCancellation
import team.startup.expo.domain.participation.presentation.dto.request.GetRegistrationStatusReqDto
import team.startup.expo.domain.participation.presentation.dto.response.RegistrationStatus
import team.startup.expo.domain.participation.presentation.dto.response.RegistrationStatusResDto
import team.startup.expo.domain.participation.repository.RegistrationCancellationRepository
import team.startup.expo.domain.participation.repository.RegistrationRequestRepository
import team.startup.expo.domain.participation.service.ExpoDeletionGuard
import team.startup.expo.domain.participation.service.GetRegistrationStatusService
import team.startup.expo.domain.participation.service.ParticipantRegistrationLock
import team.startup.expo.global.exception.ExpectedException

/**
 * `requestId`의 등록이 저장됐는지 확정해 답한다. [RegistrationIdempotency]와 같은 순서(삭제 guard, 요청 키 lock)로
 * lock을 잡은 채 읽으므로 진행 중이던 원래 요청이 끝난 뒤에 답한다. 기록이 없으면 `NOT_SAVED`를 답하면서 그 키를 취소 기록으로 남겨,
 * 이후 같은 키의 등록은 저장되지 않고 409가 된다(Application은 이를 확정 거절로 본다). 기록이 있는 키의 박람회가 다르면 409이다.
 * 전화번호, 코드, 요청 본문은 읽지도 돌려주지도 않는다.
 */
@Service
class GetRegistrationStatusServiceImpl(
    private val registrationRequestRepository: RegistrationRequestRepository,
    private val registrationCancellationRepository: RegistrationCancellationRepository,
    private val expoDeletionGuard: ExpoDeletionGuard,
    private val registrationLock: ParticipantRegistrationLock,
) : GetRegistrationStatusService {
    @Transactional
    override fun execute(reqDto: GetRegistrationStatusReqDto): RegistrationStatusResDto {
        expoDeletionGuard.requireNotDeleted(reqDto.expoId)
        registrationLock.lockRequest(reqDto.requestId)

        registrationRequestRepository.findById(reqDto.requestId).orElse(null)?.let {
            requireSameExpo(it.expoId, reqDto.expoId)
            return RegistrationStatusResDto(RegistrationStatus.SAVED, it.participantId)
        }
        registrationCancellationRepository.findById(reqDto.requestId).orElse(null)?.let {
            requireSameExpo(it.expoId, reqDto.expoId)
            return RegistrationStatusResDto(RegistrationStatus.NOT_SAVED)
        }
        registrationCancellationRepository.save(RegistrationCancellation(requestId = reqDto.requestId, expoId = reqDto.expoId))
        return RegistrationStatusResDto(RegistrationStatus.NOT_SAVED)
    }

    private fun requireSameExpo(
        recorded: String,
        requested: String,
    ) {
        if (recorded != requested) throw ExpectedException(HttpStatus.CONFLICT, "같은 요청 키가 다른 박람회에 쓰였습니다.")
    }
}
