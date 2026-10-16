package team.startup.expo.domain.training.service.impl

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.ExpoDeletionGuard
import team.startup.expo.domain.participation.service.ParticipantRegistrationLock
import team.startup.expo.domain.training.entity.TraineeParticipantLinkAction
import team.startup.expo.domain.training.entity.TraineeParticipantLinkAudit
import team.startup.expo.domain.training.presentation.dto.request.TraineeParticipantLinkReqDto
import team.startup.expo.domain.training.presentation.dto.response.TraineeParticipantLinkResDto
import team.startup.expo.domain.training.repository.TraineeParticipantLinkAuditRepository
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.domain.training.service.LinkTraineeParticipantService
import team.startup.expo.domain.user.entity.Authority
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.global.exception.ExpectedException

/**
 * 이어지지 않은(레거시) 연수자를 운영자가 같은 사람으로 확인한 같은 박람회의 기존 참가자에 잇거나, 그렇게 이은 연결을 푼다.
 * 이름·전화번호·대표자 번호로 사람을 찾거나 합치지 않고, 요청한 두 ID만 잇는다. 연수자와 참가자를 새로 만들거나 ID를 바꾸지
 * 않으며 연수자의 다른 값(번호, 연수원 아이디, 답변)과 그 ID에 딸린 신청·출석은 그대로 둔다.
 *
 * - 1:1: 참가자에 이미 다른 연수자가 있거나 연수자가 이미 다른 참가자에 이어져 있으면 덮어쓰지 않고 `409`이다. 어느 쪽이
 *   걸렸는지는 기존 읽기 API(연수자 상세의 `participantId`, 관리자 취소 상태의 `linkedTraineeId`)로 확인한다.
 * - 멱등: 이미 요청한 상태이면 아무것도 바꾸지 않고 기록도 남기지 않으며 `changed=false`이다.
 * - 동시성: 박람회 삭제 lock, 참가자 lock 순서로 잡아(`resolve-or-create-by-participant`와 같은 lock) 같은 참가자의 최초
 *   연결과 직렬화하고, 연수자 쪽은 조건부 갱신으로 먼저 이은 요청만 이긴다. 유일 제약(V14)은 마지막 안전망이다.
 * - 해제: 감사 기록의 마지막이 같은 참가자로의 `LINK`인 연결만 푼다. 참가자에서 만든 연수자의 연결은 풀지 않는다.
 * - 권한: 박람회 관리 권한은 호출하는 신청 서비스가 검증하고, 이 서비스는 [TraineeParticipantLinkReqDto.actorAdminId]의
 *   `Admin` 행이 관리자 권한인지 다시 확인한 뒤 누가 무슨 근거로 바꿨는지 남긴다.
 *
 * 없는 연수자·참가자와 다른 박람회의 연수자·참가자는 구분하지 않고 `404`이다.
 */
@Service
class LinkTraineeParticipantServiceImpl(
    private val traineeRepository: TraineeRepository,
    private val standardParticipantRepository: StandardParticipantRepository,
    private val auditRepository: TraineeParticipantLinkAuditRepository,
    private val adminRepository: AdminRepository,
    private val expoDeletionGuard: ExpoDeletionGuard,
    private val registrationLock: ParticipantRegistrationLock,
) : LinkTraineeParticipantService {
    @Transactional
    override fun link(reqDto: TraineeParticipantLinkReqDto): TraineeParticipantLinkResDto {
        val traineeId = requireNotNull(reqDto.traineeId)
        val participantId = requireNotNull(reqDto.participantId)
        lockAndCheck(reqDto, traineeId, participantId)
        if (!standardParticipantRepository.existsByIdAndExpoId(participantId, reqDto.expoId)) throw notFound()

        traineeRepository.findByStandardParticipantId(participantId)?.let {
            if (it.id == traineeId) return TraineeParticipantLinkResDto(traineeId, participantId, changed = false)
            throw ExpectedException(HttpStatus.CONFLICT, "참가자에 이미 다른 연수자가 이어져 있습니다.")
        }
        val updated =
            try {
                traineeRepository.linkParticipantIfUnlinked(traineeId, participantId)
            } catch (exception: DataIntegrityViolationException) {
                throw ExpectedException(HttpStatus.CONFLICT, "참가자에 이미 다른 연수자가 이어져 있습니다.")
            }
        if (updated == 0) throw ExpectedException(HttpStatus.CONFLICT, "연수자가 이미 다른 참가자에 이어져 있습니다.")

        audit(reqDto, traineeId, participantId, TraineeParticipantLinkAction.LINK)
        return TraineeParticipantLinkResDto(traineeId, participantId, changed = true)
    }

    @Transactional
    override fun revoke(reqDto: TraineeParticipantLinkReqDto): TraineeParticipantLinkResDto {
        val traineeId = requireNotNull(reqDto.traineeId)
        val participantId = requireNotNull(reqDto.participantId)
        lockAndCheck(reqDto, traineeId, participantId)

        // 이 연수자의 연결을 바꾸는 요청은 모두 지금 이어진 참가자의 lock을 잡으므로 아래 읽기와 갱신 사이에 바뀌지 않는다
        when (traineeRepository.findStandardParticipantIdById(traineeId)) {
            null -> return TraineeParticipantLinkResDto(traineeId, null, changed = false)
            participantId -> Unit
            else -> throw ExpectedException(HttpStatus.CONFLICT, "연수자가 다른 참가자에 이어져 있습니다.")
        }
        val last = auditRepository.findFirstByTraineeIdOrderByIdDesc(traineeId)
        if (last?.action != TraineeParticipantLinkAction.LINK || last.standardParticipantId != participantId) {
            throw ExpectedException(HttpStatus.CONFLICT, "귀속 확인으로 이은 연결만 해제할 수 있습니다.")
        }
        if (traineeRepository.unlinkParticipantIfLinkedTo(traineeId, participantId) == 0) {
            throw ExpectedException(HttpStatus.CONFLICT, "연수자가 다른 참가자에 이어져 있습니다.")
        }

        audit(reqDto, traineeId, participantId, TraineeParticipantLinkAction.REVOKE)
        return TraineeParticipantLinkResDto(traineeId, null, changed = true)
    }

    private fun lockAndCheck(
        reqDto: TraineeParticipantLinkReqDto,
        traineeId: Long,
        participantId: Long,
    ) {
        expoDeletionGuard.requireNotDeleted(reqDto.expoId)
        registrationLock.lockParticipant(participantId)
        val actor = adminRepository.findById(requireNotNull(reqDto.actorAdminId)).orElse(null)
        if (actor?.authority != Authority.ROLE_ADMIN) throw ExpectedException(HttpStatus.FORBIDDEN, "연결을 바꿀 권한이 없습니다.")
        traineeRepository.findByIdAndExpoId(traineeId, reqDto.expoId) ?: throw notFound()
    }

    private fun audit(
        reqDto: TraineeParticipantLinkReqDto,
        traineeId: Long,
        participantId: Long,
        action: TraineeParticipantLinkAction,
    ) {
        auditRepository.save(
            TraineeParticipantLinkAudit(
                traineeId = traineeId,
                standardParticipantId = participantId,
                expoId = reqDto.expoId,
                action = action,
                actorAdminId = requireNotNull(reqDto.actorAdminId),
                reason = reqDto.reason,
            ),
        )
    }

    private fun notFound() = ExpectedException(HttpStatus.NOT_FOUND, "행사 연수자나 참가자를 찾지 못 했습니다.")
}
