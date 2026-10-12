package team.startup.expo.domain.participation.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.presentation.dto.request.GetAdminCancellationStatusReqDto
import team.startup.expo.domain.participation.presentation.dto.response.AdminCancellationStatusResDto
import team.startup.expo.domain.participation.repository.StandardParticipantParticipationRepository
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.GetAdminCancellationStatusService
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.global.exception.ExpectedException

/**
 * 관리자 취소 전에 참가자의 입장 여부와 이어진 연수자를 읽기 전용으로 알려 준다. 참여 서비스가 취소 보호를 걸 때 부른다.
 * 소유·대표자 확인과 전화번호는 없다. 박람회 관리자 권한은 호출하는 신청 서비스가 이미 검증한 경계 안의 호출이라는 전제이며,
 * `X-Internal-Token`은 서비스 공용이라 호출 서비스를 이 API에서 가릴 수는 없다.
 *
 * 없는 참가자와 다른 박람회의 참가자를 구분하지 않고 같은 `404`로 실패한다. 참가자·연수자·연결을 만들거나 기록하지 않고
 * 잠그지도 않으며 전화번호와 `code`는 받지도 응답에 싣지도 않는다.
 */
@Service
class GetAdminCancellationStatusServiceImpl(
    private val standardParticipantRepository: StandardParticipantRepository,
    private val participationRepository: StandardParticipantParticipationRepository,
    private val traineeRepository: TraineeRepository,
) : GetAdminCancellationStatusService {
    @Transactional(readOnly = true)
    override fun execute(reqDto: GetAdminCancellationStatusReqDto): AdminCancellationStatusResDto {
        val participantId = requireNotNull(reqDto.participantId)
        // 엔티티 전체(information_json 포함)를 읽지 않고 이 박람회의 참가자인지만 확인한다
        if (!standardParticipantRepository.existsByIdAndExpoId(participantId, reqDto.expoId)) {
            throw ExpectedException(HttpStatus.NOT_FOUND, "행사 참가자를 찾지 못 했습니다.")
        }

        val linkedTraineeId = traineeRepository.findByStandardParticipantId(participantId)?.id
        return AdminCancellationStatusResDto(
            participantId = participantId,
            entered = participationRepository.existsByStandardParticipantId(participantId),
            traineeLinked = linkedTraineeId != null,
            linkedTraineeId = linkedTraineeId,
        )
    }
}
