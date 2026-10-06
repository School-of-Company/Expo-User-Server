package team.startup.expo.domain.participation.service.impl

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.presentation.dto.request.CreateStandardParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.response.CreateStandardParticipantResDto
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.CreateStandardParticipantService
import team.startup.expo.domain.participation.service.ParticipantRegistrationLock
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.util.ConstraintViolations
import team.startup.expo.global.util.InformationJson
import team.startup.expo.global.util.PhoneNumbers
import java.time.LocalDateTime

/**
 * v1 규칙 그대로다. 같은 박람회에 같은 번호의 참가자가 있으면 QR 문자를 두 번 보낸 뒤(`smsTryTime >= 2`)에만
 * 거부하고, 아니면 새로 만들지 않고 기존 참가자로 성공한다(QR을 다시 보내기 위해서다). 없으면 `smsTryTime = 0`으로 만든다.
 *
 * 같은 번호(숫자 기준)의 요청은 [ParticipantRegistrationLock]으로 직렬화해서, 확인과 저장 사이에 다른 요청이 끼어
 * 표기만 다른 같은 번호의 행이 둘 생기는 것을 막는다. 유일 제약은 마지막 안전망이라, 걸리면 409로 바꾼다.
 */
@Service
class CreateStandardParticipantServiceImpl(
    private val standardParticipantRepository: StandardParticipantRepository,
    private val registrationLock: ParticipantRegistrationLock,
) : CreateStandardParticipantService {
    @Transactional
    override fun execute(reqDto: CreateStandardParticipantReqDto): CreateStandardParticipantResDto {
        InformationJson.requireValid(reqDto.informationJson)
        val digits = PhoneNumbers.digitsOnly(reqDto.phoneNumber)
        // 표기만 다른 같은 번호의 동시 등록도 하나씩 처리해야 아래 확인이 앞선 요청의 저장을 볼 수 있다
        registrationLock.lockPhone(reqDto.expoId, digits)

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
