package team.startup.expo.domain.training.service.impl

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.ExpoDeletionGuard
import team.startup.expo.domain.participation.service.ParticipantRegistrationLock
import team.startup.expo.domain.training.entity.Trainee
import team.startup.expo.domain.training.presentation.dto.request.ResolveOrCreateTraineeByParticipantReqDto
import team.startup.expo.domain.training.presentation.dto.response.ResolveOrCreateTraineeByParticipantResDto
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.domain.training.service.ResolveOrCreateTraineeByParticipantService
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.util.ConstraintViolations
import java.time.LocalDateTime

/**
 * 사전등록 참가자에 이어진 연수자를 찾거나, 없으면 처음 한 번 만든다. 연수마다 사람을 새로 만들지 않고 같은 참가자는 다른
 * 연수에서도 같은 연수자를 다시 쓴다(참가자와 연수자는 1:1이다). 번호로 사람을 합치지 않는다. 대표자와 동행자가 같은 수신
 * 번호를 써도 참가자마다 다른 연수자가 되도록, 새 연수자는 전화번호를 비우고 문자 번호만(`notificationPhoneNumber`) 갖는다.
 *
 * 이미 이어진 연수자는 요청 값으로 덮어쓰지 않고 그대로 돌려준다(연수원 아이디를 고치는 일은 이 호출의 범위가 아니다).
 * 박람회 삭제 lock, 참가자 lock 순서로 잡아 삭제와 동시 최초 호출을 직렬화하고, 유일 제약(참가자당 연수자 하나)은 마지막
 * 안전망이다. 이름과 개인정보 동의는 참가자에서 가져온다.
 */
@Service
class ResolveOrCreateTraineeByParticipantServiceImpl(
    private val traineeRepository: TraineeRepository,
    private val standardParticipantRepository: StandardParticipantRepository,
    private val expoDeletionGuard: ExpoDeletionGuard,
    private val registrationLock: ParticipantRegistrationLock,
) : ResolveOrCreateTraineeByParticipantService {
    @Transactional
    override fun execute(reqDto: ResolveOrCreateTraineeByParticipantReqDto): ResolveOrCreateTraineeByParticipantResDto {
        val participantId = requireNotNull(reqDto.participantId)
        // 이미 이어진 연수자를 돌려주는 경로도 삭제 중인 박람회의 곧 사라질 ID를 주지 않도록 가장 먼저 확인한다
        expoDeletionGuard.requireNotDeleted(reqDto.expoId)
        registrationLock.lockParticipant(participantId)

        val participant =
            standardParticipantRepository.findByIdAndExpoId(participantId, reqDto.expoId)
                ?: throw ExpectedException(HttpStatus.NOT_FOUND, "행사 참가자를 찾지 못 했습니다.")
        traineeRepository.findByStandardParticipantId(participantId)?.let {
            return ResolveOrCreateTraineeByParticipantResDto(requireNotNull(it.id), created = false)
        }

        // 동행자는 번호가 없어 문자를 대표자 번호로 보낸다(입장 기록 응답과 같은 규칙)
        val notificationPhoneNumber =
            participant.phoneNumber
                ?: participant.representativeId?.let { standardParticipantRepository.findById(it).orElse(null)?.phoneNumber }
        val saved =
            try {
                traineeRepository.save(
                    Trainee(
                        expoId = reqDto.expoId,
                        name = participant.name,
                        phoneNumber = null,
                        trainingId = reqDto.trainingId,
                        personalInformationStatus = participant.personalInformationStatus,
                        applicationType = participant.applicationType,
                        applicationDate = LocalDateTime.now(),
                        school = reqDto.school ?: participant.school,
                        notificationPhoneNumber = notificationPhoneNumber,
                        standardParticipantId = participantId,
                    ),
                )
            } catch (exception: DataIntegrityViolationException) {
                throw ConstraintViolations.translate(exception)
            }
        return ResolveOrCreateTraineeByParticipantResDto(requireNotNull(saved.id), created = true)
    }
}
