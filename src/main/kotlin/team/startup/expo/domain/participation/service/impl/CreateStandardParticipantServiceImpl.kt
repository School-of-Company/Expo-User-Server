package team.startup.expo.domain.participation.service.impl

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.presentation.dto.request.CreateStandardParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.response.CreateStandardParticipantResDto
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.CreateStandardParticipantService
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.util.ConstraintViolations
import team.startup.expo.global.util.InformationJson
import team.startup.expo.global.util.PhoneNumbers
import java.time.LocalDateTime

/**
 * v1 규칙 그대로다. 같은 박람회에 같은 번호의 참가자가 있으면 QR 문자를 두 번 보낸 뒤(`smsTryTime >= 2`)에만
 * 거부하고, 아니면 새로 만들지 않고 기존 참가자로 성공한다(QR을 다시 보내기 위해서다). 없으면 `smsTryTime = 0`으로 만든다.
 *
 * 저장은 서비스 전체를 하나의 트랜잭션으로 묶지 않고 저장소 호출 단위로 한다. 동시 요청이 유일 제약에 걸려도
 * 트랜잭션이 이미 중단된 상태가 아니라서 여기서 409로 바꿀 수 있다.
 */
@Service
class CreateStandardParticipantServiceImpl(
    private val standardParticipantRepository: StandardParticipantRepository,
) : CreateStandardParticipantService {
    override fun execute(reqDto: CreateStandardParticipantReqDto): CreateStandardParticipantResDto {
        InformationJson.requireValid(reqDto.informationJson)
        val digits = PhoneNumbers.digitsOnly(reqDto.phoneNumber)

        val existing =
            standardParticipantRepository.findByExpoIdAndPhoneNumber(reqDto.expoId, reqDto.phoneNumber)
                ?: PhoneNumbers.select(standardParticipantRepository.findAllByExpoIdAndDigits(reqDto.expoId, digits), reqDto.phoneNumber) {
                    it.phoneNumber
                }
        if (existing != null) {
            if (existing.smsTryTime >= MAX_SMS_TRY_TIME) {
                throw ExpectedException(HttpStatus.CONFLICT, "이미 신청한 참가자입니다.")
            }
            return CreateStandardParticipantResDto(existing.id!!, existing.phoneNumber, created = false)
        }

        val saved =
            try {
                standardParticipantRepository.save(
                    StandardParticipant(
                        expoId = reqDto.expoId,
                        name = reqDto.name,
                        phoneNumber = reqDto.phoneNumber,
                        informationJson = reqDto.informationJson,
                        personalInformationStatus = reqDto.personalInformationStatus,
                        applicationType = reqDto.applicationType,
                        applicationDate = LocalDateTime.now(),
                        occupation = reqDto.occupation,
                        school = reqDto.school,
                    ),
                )
            } catch (exception: DataIntegrityViolationException) {
                throw ConstraintViolations.translate(exception)
            }
        return CreateStandardParticipantResDto(saved.id!!, saved.phoneNumber, created = true)
    }

    private companion object {
        const val MAX_SMS_TRY_TIME = 2
    }
}
