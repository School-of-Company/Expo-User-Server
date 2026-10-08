package team.startup.expo.domain.training.service.impl

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.service.ExpoDeletionGuard
import team.startup.expo.domain.participation.service.ParticipantRegistrationLock
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.domain.training.entity.Trainee
import team.startup.expo.domain.training.presentation.dto.request.ResolveOrCreateTraineeReqDto
import team.startup.expo.domain.training.presentation.dto.response.ResolveOrCreateTraineeResDto
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.domain.training.service.ResolveOrCreateTraineeService
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.util.ConstraintViolations
import team.startup.expo.global.util.InformationJson
import team.startup.expo.global.util.PhoneNumbers
import team.startup.expo.global.util.QuestionSnapshot
import java.time.LocalDateTime

/**
 * 연수 프로그램 신청 흐름이 연수자를 만들거나 다시 쓰려고 부른다. v1 `ApplicationTrainingProListAndTraineeServiceImpl.saveTrainee`와
 * 같게, 같은 박람회에 같은 번호의 연수자가 있으면 그 연수자를 그대로 돌려주고(요청 값으로 덮어쓰지 않는다) 없으면
 * `PRE`로 만든다. 박람회 등록(`POST /internal/trainees`)과 달리 연수 번호나 전화번호가 겹쳐도 거부하지 않는다.
 *
 * v1은 연수 번호(`trainingId`)가 겹치는지 보지 않으므로 같은 번호에 다른 연수 번호가 와도 기존 연수자를 쓰고,
 * 다른 번호에 같은 연수 번호가 와도 새로 만든다. 이렇게 같은 연수 번호가 둘 생기면 연수 번호로 찾는 `/internal/trainees/resolve`는
 * 하나로 특정할 수 없어 409로 실패한다. 이 정책은 Expo 담당자와 합의가 필요한 열린 결정이다(`#42`). 같은 요청을 다시 보내면(Application 신청이 실패해 재시도하는 경우)
 * 같은 `traineeId`를 돌려준다. 같은 번호의 동시 요청은 [ParticipantRegistrationLock]으로 직렬화해 하나만 만든다.
 */
@Service
class ResolveOrCreateTraineeServiceImpl(
    private val traineeRepository: TraineeRepository,
    private val registrationLock: ParticipantRegistrationLock,
    private val expoDeletionGuard: ExpoDeletionGuard,
) : ResolveOrCreateTraineeService {
    @Transactional
    override fun execute(reqDto: ResolveOrCreateTraineeReqDto): ResolveOrCreateTraineeResDto {
        InformationJson.requireValid(reqDto.informationJson)
        QuestionSnapshot.errorOf(reqDto.questions)?.let { throw ExpectedException(HttpStatus.BAD_REQUEST, it) }
        val digits = PhoneNumbers.digitsOnly(reqDto.phoneNumber)
        // 이미 있는 연수자를 돌려주는 경로도 삭제 중인 박람회의 곧 사라질 ID를 주지 않도록 가장 먼저 확인한다
        expoDeletionGuard.requireNotDeleted(reqDto.expoId)
        registrationLock.lockPhone(reqDto.expoId, digits)

        val existing =
            traineeRepository.findByExpoIdAndPhoneNumber(reqDto.expoId, reqDto.phoneNumber)
                ?: PhoneNumbers.select(traineeRepository.findAllByExpoIdAndDigits(reqDto.expoId, digits), reqDto.phoneNumber) {
                    it.phoneNumber
                }
        if (existing != null) return ResolveOrCreateTraineeResDto(existing.id!!, created = false)

        val saved =
            try {
                traineeRepository.save(
                    Trainee(
                        expoId = reqDto.expoId,
                        name = reqDto.name,
                        phoneNumber = reqDto.phoneNumber,
                        trainingId = reqDto.trainingId,
                        informationJson = reqDto.informationJson,
                        personalInformationStatus = reqDto.personalInformationStatus,
                        applicationType = ApplicationType.PRE,
                        applicationDate = LocalDateTime.now(),
                        school = reqDto.school,
                        informationFormId = reqDto.formId,
                        informationQuestions = QuestionSnapshot.serialize(reqDto.questions),
                    ),
                )
            } catch (exception: DataIntegrityViolationException) {
                throw ConstraintViolations.translate(exception)
            }
        return ResolveOrCreateTraineeResDto(saved.id!!, created = true)
    }
}
