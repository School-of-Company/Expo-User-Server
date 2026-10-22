package team.startup.expo.domain.participation.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.entity.ParticipationType
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.presentation.dto.request.CreateFieldVisitParticipantsReqDto
import team.startup.expo.domain.participation.presentation.dto.request.FieldVisitStatus
import team.startup.expo.domain.participation.presentation.dto.request.ProjectFieldVisitReqDto
import team.startup.expo.domain.participation.presentation.dto.response.FieldVisitParticipantResDto
import team.startup.expo.domain.participation.repository.FieldVisitRepository
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.ExpoDeletionGuard
import team.startup.expo.domain.participation.service.FieldVisitService
import team.startup.expo.domain.participation.service.ParticipantRegistrationLock
import team.startup.expo.domain.participation.service.RegisteredParticipant
import team.startup.expo.domain.participation.service.RegistrationOutboxWriter
import team.startup.expo.domain.participation.service.RepresentativeFinder
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.util.InformationJson
import team.startup.expo.global.util.PhoneNumbers
import team.startup.expo.global.util.QuestionSnapshot
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@Service
class FieldVisitServiceImpl(
    private val participants: StandardParticipantRepository,
    private val visits: FieldVisitRepository,
    private val locks: ParticipantRegistrationLock,
    private val deletionGuard: ExpoDeletionGuard,
    private val representatives: RepresentativeFinder,
    private val outbox: RegistrationOutboxWriter,
    private val json: JsonMapper,
) : FieldVisitService {
    @Transactional
    override fun create(
        expoId: String,
        body: CreateFieldVisitParticipantsReqDto,
    ): List<FieldVisitParticipantResDto> {
        deletionGuard.requireNotDeleted(expoId)
        locks.lockRequest("field-create:$expoId:${body.requestId}")
        val fingerprint = hash(json.writeValueAsString(body))
        visits.creation(expoId, body.requestId)?.let {
            if (it.fingerprint != fingerprint) throw conflict("같은 요청 키로 다른 내용을 보낼 수 없습니다.")
            return json.readValue(it.responseJson, object : TypeReference<List<FieldVisitParticipantResDto>>() {})
        }
        if (body.participants.isEmpty() || body.participants.size > 5 ||
            body.participants
                .map { it.clientReference }
                .distinct()
                .size != body.participants.size
        ) {
            throw ExpectedException(HttpStatus.BAD_REQUEST, "신규 참가자는 1~5명이며 clientReference는 서로 달라야 합니다.")
        }
        QuestionSnapshot.errorOf(body.questions)?.let { throw ExpectedException(HttpStatus.BAD_REQUEST, it) }
        body.participants.forEach { InformationJson.requireValid(it.informationJson) }
        locks.lockPhone(expoId, PhoneNumbers.digitsOnly(body.phoneNumber))
        var representative = representatives.find(expoId, body.phoneNumber)
        if (representative != null && visits.hasPending(representative.id!!)) {
            throw conflict("완료되지 않은 신규 참가자 생성 요청을 같은 요청 키로 복구해야 합니다.")
        }
        val reserved = representative?.let { visits.reservedCount(it.id!!) } ?: 0
        if (reserved + body.participants.size > 5) throw conflict("대표자를 포함해 최대 5명까지 신청할 수 있습니다.")
        val created =
            body.participants.map { person ->
                participants
                    .saveAndFlush(
                        StandardParticipant(
                            expoId = expoId,
                            name = person.name,
                            phoneNumber = if (representative == null) body.phoneNumber else null,
                            personalInformationStatus = body.personalInformationStatus,
                            applicationType = ApplicationType.FIELD,
                            applicationDate = LocalDateTime.now(ZoneId.of("Asia/Seoul")),
                            occupation = person.occupation,
                            school = person.school,
                            region = person.region,
                            representativeId = representative?.id,
                            informationJson = person.informationJson,
                            informationFormId = body.formId,
                            informationQuestions = QuestionSnapshot.serialize(body.questions),
                        ),
                    ).also {
                        if (representative == null) representative = it
                        visits.markNew(it.id!!)
                    }
            }
        val owner = requireNotNull(representative)
        outbox.registered(
            expoId,
            ParticipationType.STANDARD,
            owner.id!!,
            owner.phoneNumber!!,
            created.map {
                RegisteredParticipant(it.id!!, it.code)
            },
        )
        created.forEach { outbox.standardCreated(expoId, it.id!!, it.phoneNumber) }
        val result =
            created.zip(
                body.participants,
            ) { participant, person -> FieldVisitParticipantResDto(participant.id!!, person.clientReference) }
        visits.saveCreation(expoId, body.requestId, fingerprint, json.writeValueAsString(result))
        return result
    }

    @Transactional
    override fun project(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        body: ProjectFieldVisitReqDto,
    ) {
        if (body.generation <= 0) throw ExpectedException(HttpStatus.BAD_REQUEST, "generation은 양수여야 합니다.")
        deletionGuard.requireNotDeleted(expoId)
        // operation과 등록 UUID를 차례로 잠가 다른 등록에 같은 operation을 쓰는 요청도 직렬화한다.
        locks.lockRequest("field-operation:${body.operationId}")
        locks.lockRequest("field-registration:$registrationId")
        val fingerprint = hash("$expoId:$participantId:$registrationId:${json.writeValueAsString(body)}")
        visits.operation(body.operationId)?.let {
            if (it != fingerprint) throw conflict("operationId를 다른 내용으로 사용할 수 없습니다.")
            return
        }
        val participant =
            participants.findByIdAndExpoId(participantId, expoId) ?: throw ExpectedException(HttpStatus.NOT_FOUND, "참가자를 찾을 수 없습니다.")
        val owner = participant.representativeId?.let { participants.findByIdAndExpoId(it, expoId) } ?: participant
        locks.lockPhone(expoId, PhoneNumbers.digitsOnly(requireNotNull(owner.phoneNumber)))
        val previous = visits.projection(registrationId)
        if (previous != null &&
            (previous.expoId != expoId || previous.participantId != participantId || previous.visitDate != body.visitDate)
        ) {
            throw conflict("등록의 참가자와 방문일은 바꿀 수 없습니다.")
        }
        val ignored =
            previous != null &&
                (
                    body.generation < previous.generation ||
                        (body.generation == previous.generation && previous.status == FieldVisitStatus.CANCELLED)
                )
        if (!ignored) {
            if (body.status == FieldVisitStatus.ACTIVE && !visits.reserved(participantId)) {
                if (visits.reservedCount(owner.id!!) >= 5) throw conflict("대표자를 포함해 최대 5명까지 신청할 수 있습니다.")
            }
            visits.project(expoId, participantId, registrationId, body)
        }
        visits.saveOperation(body.operationId, expoId, fingerprint)
    }

    private fun conflict(message: String) = ExpectedException(HttpStatus.CONFLICT, message)

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") {
            "%02x".format(it)
        }
}
