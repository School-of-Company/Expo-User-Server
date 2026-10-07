package team.startup.expo.domain.participation.service.impl

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.entity.ParticipationType
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.entity.StandardParticipantParticipation
import team.startup.expo.domain.participation.presentation.dto.request.RecordEntryReqDto
import team.startup.expo.domain.participation.presentation.dto.response.RecordEntryResDto
import team.startup.expo.domain.participation.repository.StandardParticipantParticipationRepository
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.RecordEntryService
import team.startup.expo.domain.training.entity.Trainee
import team.startup.expo.domain.training.entity.TraineeParticipation
import team.startup.expo.domain.training.repository.TraineeParticipationRepository
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.util.ParticipantCode
import team.startup.expo.global.util.PhoneNumbers
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 박람회 입장 스캔이 부른다. v1 `PreEnterScanQrCodeServiceImpl`의 참가자 조회와 입장 기록 부분이다.
 * 박람회 기간 확인과 문자 이벤트는 참여 서비스가 한다.
 *
 * 일반 참가자는 `participantId`와 `code`가 오면 그 둘로 찾고(동행자는 번호가 없다), 아니면 `(박람회, 전화번호)`로 찾는다. 연수자는 `(박람회, 전화번호)`로 찾는다. 하루에 한 번만 입장하며(`(expo_id, 참가자, 날짜)`
 * 유일 제약) 날이 바뀌면 다시 입장할 수 있다. 조회와 기록은 한 트랜잭션이고, 같은 참가자의 동시 스캔은
 * 유일 제약이 막아 한쪽만 기록되고 나머지는 409이다. 날짜는 날짜별 입장자 목록과 같게 한국 시간 기준이다.
 */
@Service
class RecordEntryServiceImpl(
    private val standardParticipantRepository: StandardParticipantRepository,
    private val standardParticipantParticipationRepository: StandardParticipantParticipationRepository,
    private val traineeRepository: TraineeRepository,
    private val traineeParticipationRepository: TraineeParticipationRepository,
) : RecordEntryService {
    @Transactional
    override fun execute(reqDto: RecordEntryReqDto): RecordEntryResDto {
        val now = LocalDateTime.now(SEOUL)
        return when (reqDto.participationType) {
            ParticipationType.STANDARD -> {
                val participant = findStandard(reqDto)
                record {
                    standardParticipantParticipationRepository.saveAndFlush(
                        StandardParticipantParticipation(
                            entryTime = now,
                            attendanceDate = now.toLocalDate(),
                            standardParticipant = participant,
                            expoId = reqDto.expoId,
                        ),
                    )
                }
                standardResponse(participant)
            }

            ParticipationType.TRAINEE -> {
                val phoneNumber = reqDto.phoneNumber?.takeIf { it.isNotBlank() } ?: throw requirePhone()
                val digits = PhoneNumbers.digitsOnly(phoneNumber)
                val trainee =
                    traineeRepository.findByExpoIdAndPhoneNumber(reqDto.expoId, phoneNumber)
                        ?: PhoneNumbers.select(traineeRepository.findAllByExpoIdAndDigits(reqDto.expoId, digits), phoneNumber) {
                            it.phoneNumber
                        }
                        ?: throw notFound()
                record {
                    traineeParticipationRepository.saveAndFlush(
                        TraineeParticipation(
                            entryTime = now,
                            attendanceDate = now.toLocalDate(),
                            trainee = trainee,
                            expoId = reqDto.expoId,
                        ),
                    )
                }
                traineeResponse(trainee)
            }
        }
    }

    /**
     * `participantId`와 `code`가 오면 그 둘로 찾고, 없으면 번호로 찾는다. 참가자가 없거나 `code`가 다르면 같은 404로 답해
     * 남의 참가자 ID가 있는지 드러나지 않게 한다.
     */
    private fun findStandard(reqDto: RecordEntryReqDto): StandardParticipant {
        if (reqDto.participantId != null || reqDto.code != null) {
            val participant =
                reqDto.participantId?.let { standardParticipantRepository.findByIdAndExpoId(it, reqDto.expoId) } ?: throw notFound()
            if (reqDto.code == null || !ParticipantCode.matches(participant.code, reqDto.code)) throw notFound()
            return participant
        }
        val phoneNumber = reqDto.phoneNumber?.takeIf { it.isNotBlank() } ?: throw requirePhone()
        val digits = PhoneNumbers.digitsOnly(phoneNumber)
        return standardParticipantRepository.findByExpoIdAndPhoneNumber(reqDto.expoId, phoneNumber)
            ?: PhoneNumbers.select(standardParticipantRepository.findAllByExpoIdAndDigits(reqDto.expoId, digits), phoneNumber) {
                it.phoneNumber
            }
            ?: throw notFound()
    }

    private fun requirePhone() = ExpectedException(HttpStatus.BAD_REQUEST, "phoneNumber 또는 participantId와 code가 필요합니다.")

    private fun record(save: () -> Unit) {
        try {
            save()
        } catch (_: DataIntegrityViolationException) {
            throw ExpectedException(HttpStatus.CONFLICT, "오늘 이미 입장했습니다.")
        }
    }

    private fun notFound() = ExpectedException(HttpStatus.NOT_FOUND, "행사 참가자를 찾지 못 했습니다.")

    private fun standardResponse(participant: StandardParticipant): RecordEntryResDto {
        // 동행자는 번호가 없어 문자를 대표자 번호로 보낸다
        val notificationPhoneNumber =
            participant.phoneNumber
                ?: participant.representativeId?.let { standardParticipantRepository.findById(it).orElse(null)?.phoneNumber }
        return RecordEntryResDto(
            id = participant.id!!,
            name = participant.name,
            phoneNumber = participant.phoneNumber,
            notificationPhoneNumber = notificationPhoneNumber,
            personalInformationStatus = participant.personalInformationStatus,
            participationType = ParticipationType.STANDARD,
            occupation = participant.occupation,
            school = participant.school,
        )
    }

    private fun traineeResponse(trainee: Trainee) =
        RecordEntryResDto(
            id = trainee.id!!,
            name = trainee.name,
            phoneNumber = trainee.phoneNumber,
            notificationPhoneNumber = trainee.phoneNumber,
            personalInformationStatus = trainee.personalInformationStatus,
            participationType = ParticipationType.TRAINEE,
            occupation = null,
            school = trainee.school,
        )

    private companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
