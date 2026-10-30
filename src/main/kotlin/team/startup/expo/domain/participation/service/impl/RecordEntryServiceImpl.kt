package team.startup.expo.domain.participation.service.impl

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.entity.BadgePrintResult
import team.startup.expo.domain.participation.entity.Occupation
import team.startup.expo.domain.participation.entity.ParticipationType
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.entity.StandardParticipantParticipation
import team.startup.expo.domain.participation.presentation.dto.request.FieldVisitStatus
import team.startup.expo.domain.participation.presentation.dto.request.RecordEntryReqDto
import team.startup.expo.domain.participation.presentation.dto.response.RecordEntryResDto
import team.startup.expo.domain.participation.repository.BadgePrintRepository
import team.startup.expo.domain.participation.repository.FieldVisitRepository
import team.startup.expo.domain.participation.repository.StandardParticipantParticipationRepository
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.RecordEntryService
import team.startup.expo.domain.training.entity.ApplicationType
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
 * 일반 참가자는 `participantId`와 `code`가 오면 그 둘로 찾고(동행자는 번호가 없다), 아니면 `(박람회, 전화번호)`로 찾는다. 연수자는 `(박람회, 전화번호)`로 찾는다. 하루에 회차별로 한 번만 입장하며(`(expo_id, 참가자, 날짜, 회차)`
 * 유일 제약) 날이 바뀌면 다시 입장할 수 있다. 조회와 기록은 한 트랜잭션이고, 같은 참가자의 동시 스캔은
 * 유일 제약이 막아 한쪽만 기록되고 나머지는 409이다. 날짜는 날짜별 입장자 목록과 같게 한국 시간 기준이다.
 *
 * 사전등록 회차는 입장 스캔이 판정해 `preregisterSessionId`로 보낸다. 참가자의 신청 회차는 신청 서비스가 입장 서비스에만
 * 알려 주므로 이 서비스는 판정하지 않고 받은 값을 그대로 남긴다. 값이 없으면 회차 없는 입장이다.
 *
 * 명찰 자동 출력 대상(연수자 전원, 일반 참가자 중 교사·예비교사)이면 같은 트랜잭션에서 출력 작업을 저장한다. 저장이 실패하면
 * 입장도 함께 되돌린다. 작업은 (박람회, 동일인, 방문일)마다 하나라 같은 날 다른 회차로 다시 입장하거나 참가자와 연결된 연수자로
 * 입장해도 새로 만들지 않는다(`badgePrint = DUPLICATE`). 인쇄는 응답 뒤 출력대가 비동기로 한다(`BadgePrintService`).
 */
@Service
class RecordEntryServiceImpl(
    private val standardParticipantRepository: StandardParticipantRepository,
    private val standardParticipantParticipationRepository: StandardParticipantParticipationRepository,
    private val traineeRepository: TraineeRepository,
    private val traineeParticipationRepository: TraineeParticipationRepository,
    private val fieldVisits: FieldVisitRepository,
    private val badgePrintRepository: BadgePrintRepository,
) : RecordEntryService {
    @Transactional
    override fun execute(reqDto: RecordEntryReqDto): RecordEntryResDto {
        val now = LocalDateTime.now(SEOUL)
        val field = reqDto.fieldVisitRegistrationId != null || reqDto.fieldVisitGeneration != null
        if (field && (
                reqDto.attendanceDate == null || reqDto.fieldVisitRegistrationId == null ||
                    reqDto.fieldVisitGeneration == null || reqDto.fieldVisitGeneration <= 0 ||
                    reqDto.preregisterSessionId != null || reqDto.participationType != ParticipationType.STANDARD
            )
        ) {
            throw ExpectedException(
                HttpStatus.BAD_REQUEST,
                "FIELD 입장은 attendanceDate, fieldVisitRegistrationId, 양수 fieldVisitGeneration이 필요하며 회차와 함께 보낼 수 없습니다.",
            )
        }
        val attendanceDate = reqDto.attendanceDate ?: now.toLocalDate()
        return when (reqDto.participationType) {
            ParticipationType.STANDARD -> {
                val participant = findStandard(reqDto)
                if (!field && reqDto.preregisterSessionId == null &&
                    (
                        (reqDto.attendanceDate != null && participant.applicationType == ApplicationType.FIELD) ||
                            fieldVisits.requiresProjection(participant.id!!)
                    )
                ) {
                    throw ExpectedException(HttpStatus.CONFLICT, "FIELD 참가자의 방문일 등록 권위가 필요합니다.")
                }
                if (field) {
                    val visit = fieldVisits.projection(requireNotNull(reqDto.fieldVisitRegistrationId))
                    if (visit == null || visit.expoId != reqDto.expoId || visit.participantId != participant.id ||
                        visit.visitDate != attendanceDate || visit.generation != reqDto.fieldVisitGeneration ||
                        visit.status != FieldVisitStatus.ACTIVE
                    ) {
                        throw ExpectedException(HttpStatus.CONFLICT, "유효한 방문일 등록을 확인할 수 없습니다.")
                    }
                }
                record {
                    standardParticipantParticipationRepository.saveAndFlush(
                        StandardParticipantParticipation(
                            entryTime = now,
                            attendanceDate = attendanceDate,
                            standardParticipant = participant,
                            expoId = reqDto.expoId,
                            preregisterSessionId = reqDto.preregisterSessionId,
                        ),
                    )
                }
                val badgePrint =
                    if (participant.occupation in BADGE_OCCUPATIONS) {
                        enqueueBadge(
                            reqDto.expoId,
                            "S:${participant.id}",
                            attendanceDate,
                            ParticipationType.STANDARD,
                            participant.id!!,
                            participant.name,
                            participant.school,
                            now,
                        )
                    } else {
                        null
                    }
                standardResponse(participant).copy(badgePrint = badgePrint)
            }

            ParticipationType.TRAINEE -> {
                if (reqDto.preregisterSessionId != null) {
                    throw ExpectedException(HttpStatus.BAD_REQUEST, "연수자 입장에는 사전등록 회차를 보낼 수 없습니다.")
                }
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
                            attendanceDate = attendanceDate,
                            trainee = trainee,
                            expoId = reqDto.expoId,
                        ),
                    )
                }
                val personKey = trainee.standardParticipantId?.let { "S:$it" } ?: "T:${trainee.id}"
                val badgePrint =
                    enqueueBadge(
                        reqDto.expoId,
                        personKey,
                        attendanceDate,
                        ParticipationType.TRAINEE,
                        trainee.id!!,
                        trainee.name,
                        trainee.school,
                        now,
                    )
                traineeResponse(trainee).copy(badgePrint = badgePrint)
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

    private fun enqueueBadge(
        expoId: String,
        personKey: String,
        attendanceDate: LocalDate,
        participationType: ParticipationType,
        subjectId: Long,
        name: String,
        school: String?,
        now: LocalDateTime,
    ): BadgePrintResult =
        if (badgePrintRepository.insertAutoJob(expoId, personKey, attendanceDate, participationType, subjectId, name, school, now)) {
            BadgePrintResult.QUEUED
        } else {
            BadgePrintResult.DUPLICATE
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
            notificationPhoneNumber = trainee.notificationPhoneNumber ?: trainee.phoneNumber,
            personalInformationStatus = trainee.personalInformationStatus,
            participationType = ParticipationType.TRAINEE,
            occupation = null,
            school = trainee.school,
        )

    private companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

        // 명찰 자동 출력 대상 직업. 연수자는 직업이 없지만 모두 교원이라 전원 대상이다
        val BADGE_OCCUPATIONS = setOf(Occupation.TEACHER, Occupation.PRE_SERVICE_TEACHER)
    }
}
