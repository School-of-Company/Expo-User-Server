package team.startup.expo.domain.training.service.impl

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.ParticipantRegistrationLock
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.domain.training.entity.Trainee
import team.startup.expo.domain.training.presentation.dto.request.CreateTraineeReqDto
import team.startup.expo.domain.training.presentation.dto.response.CreateTraineeResDto
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.domain.training.service.CreateTraineeService
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.util.ConstraintViolations
import team.startup.expo.global.util.InformationJson
import team.startup.expo.global.util.PhoneNumbers
import java.time.LocalDateTime

/**
 * v1 규칙 그대로다.
 * - 사전 등록: 같은 박람회에 연수 번호가 같거나 전화번호가 같은 연수자가 있으면 거부한다.
 * - 현장 등록: 그 전화번호가 일반 참가자나 연수자 어느 쪽에든 있으면 거부한다.
 *
 * v1은 현장 등록한 연수자도 `PRE`로 저장했다(버그). 여기서는 요청한 등록 구분(`FIELD`)을 그대로 저장한다.
 * 확인과 저장 사이에 다른 요청이 끼어들지 못하게 [ParticipantRegistrationLock]으로 같은 번호(숫자 기준)와, 사전
 * 등록이면 같은 연수 번호의 요청을 직렬화한다. 연수 번호는 현장 등록에서 같은 값을 허용해 유일 제약을 걸 수 없고,
 * 번호의 `(expo_id, phone_number)` 유일 제약은 마지막 안전망이라 걸리면 409로 바꾼다.
 */
@Service
class CreateTraineeServiceImpl(
    private val traineeRepository: TraineeRepository,
    private val standardParticipantRepository: StandardParticipantRepository,
    private val registrationLock: ParticipantRegistrationLock,
) : CreateTraineeService {
    @Transactional
    override fun execute(reqDto: CreateTraineeReqDto): CreateTraineeResDto {
        InformationJson.requireValid(reqDto.informationJson)
        val digits = PhoneNumbers.digitsOnly(reqDto.phoneNumber)
        // 전화번호, 연수 번호 순서로 잡는다. 일반 참가자 등록도 같은 전화번호 lock을 잡아 두 테이블의 같은 번호가 함께 직렬화된다
        registrationLock.lockPhone(reqDto.expoId, digits)
        if (reqDto.applicationType == ApplicationType.PRE) registrationLock.lockTrainingId(reqDto.expoId, reqDto.trainingId)

        val alreadyApplied =
            when (reqDto.applicationType) {
                ApplicationType.PRE -> {
                    traineeRepository.existsByExpoIdAndTrainingId(reqDto.expoId, reqDto.trainingId) ||
                        traineeRepository.findAllByExpoIdAndDigits(reqDto.expoId, digits).isNotEmpty()
                }

                ApplicationType.FIELD -> {
                    traineeRepository.findAllByExpoIdAndDigits(reqDto.expoId, digits).isNotEmpty() ||
                        standardParticipantRepository.findAllByExpoIdAndDigits(reqDto.expoId, digits).isNotEmpty()
                }
            }
        if (alreadyApplied) throw ExpectedException(HttpStatus.CONFLICT, "이미 신청한 연수자입니다.")

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
                        applicationType = reqDto.applicationType,
                        applicationDate = LocalDateTime.now(),
                        school = reqDto.school,
                    ),
                )
            } catch (exception: DataIntegrityViolationException) {
                throw ConstraintViolations.translate(exception)
            }
        return CreateTraineeResDto(saved.id!!, saved.phoneNumber)
    }
}
