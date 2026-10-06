package team.startup.expo.domain.training.service.impl

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
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
 * 같은 번호의 동시 요청은 `(expo_id, phone_number)` 유일 제약이 막고 409로 바꾼다. 서비스 전체를 하나의
 * 트랜잭션으로 묶지 않아야 제약 위반 뒤에도 예외를 변환할 수 있다.
 */
@Service
class CreateTraineeServiceImpl(
    private val traineeRepository: TraineeRepository,
    private val standardParticipantRepository: StandardParticipantRepository,
) : CreateTraineeService {
    override fun execute(reqDto: CreateTraineeReqDto): CreateTraineeResDto {
        InformationJson.requireValid(reqDto.informationJson)
        val digits = PhoneNumbers.digitsOnly(reqDto.phoneNumber)

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
