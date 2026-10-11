package team.startup.expo.domain.participation.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.presentation.dto.request.GetCancellationStatusReqDto
import team.startup.expo.domain.participation.presentation.dto.response.CancellationStatusResDto
import team.startup.expo.domain.participation.repository.StandardParticipantParticipationRepository
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.GetCancellationStatusService
import team.startup.expo.domain.participation.service.RepresentativeFinder
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.global.exception.ExpectedException

/**
 * 신청 취소 전에 대상 참가자의 입장 여부와 이어진 연수자를 읽기 전용으로 알려 준다. 참가자나 연수자를 만들지 않고 아무것도
 * 잠그거나 기록하지 않는다. 검사 이후 입장·연결이 바뀌지 않게 막는 일(취소 guard)은 이 조회가 아니라 스캔을 직렬화하는 쪽의 책임이다.
 *
 * 검증된 번호의 대표자가 그 박람회에서 관리하는(본인이나 동행자) 참가자가 아니면 없는 참가자, 다른 박람회, 다른 대표자의
 * 참가자를 구분하지 않고 같은 `404`로 실패한다.
 */
@Service
class GetCancellationStatusServiceImpl(
    private val representativeFinder: RepresentativeFinder,
    private val standardParticipantRepository: StandardParticipantRepository,
    private val participationRepository: StandardParticipantParticipationRepository,
    private val traineeRepository: TraineeRepository,
) : GetCancellationStatusService {
    @Transactional(readOnly = true)
    override fun execute(reqDto: GetCancellationStatusReqDto): CancellationStatusResDto {
        val participantId = requireNotNull(reqDto.participantId)
        val representativeId = requireNotNull(representativeFinder.find(reqDto.expoId, reqDto.phoneNumber)?.id ?: throw notFound())
        val managed = standardParticipantRepository.findAllByIdOrRepresentativeIdOrderById(representativeId, representativeId)
        if (managed.none { it.id == participantId }) throw notFound()

        val linkedTraineeId = traineeRepository.findByStandardParticipantId(participantId)?.id
        return CancellationStatusResDto(
            participantId = participantId,
            representative = participantId == representativeId,
            entered = participationRepository.existsByStandardParticipantId(participantId),
            traineeLinked = linkedTraineeId != null,
            linkedTraineeId = linkedTraineeId,
        )
    }

    private fun notFound() = ExpectedException(HttpStatus.NOT_FOUND, "관리하는 참가자를 찾을 수 없습니다.")
}
