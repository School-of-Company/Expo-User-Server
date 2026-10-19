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
import team.startup.expo.domain.participation.service.RegisteredParticipant
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.util.CompanionInfo
import team.startup.expo.global.util.Companions
import team.startup.expo.global.util.ConstraintViolations
import team.startup.expo.global.util.InformationJson
import team.startup.expo.global.util.PhoneNumbers
import team.startup.expo.global.util.QuestionSnapshot
import java.time.LocalDateTime

/**
 * v1 규칙에 동행자를 더했다. 같은 박람회에 같은 번호의 대표자가 있으면 기존 대표자로 성공한다. 재신청은 `smsTryTime`과 무관하게
 * 항상 받는다. 새 동행자가 없으면 기존 대표자·동행자와 QR을 그대로 돌려주고 문자 이벤트는 만들지 않으며, 새 동행자가 있으면
 * 그 동행자만 문자 대상이다. 누적 5명 한도는 요청의 `releasedParticipantIds`(취소된 동행자)를 뺀 인원으로 센다. 없으면 `smsTryTime = 0`으로 만든다.
 *
 * 신청 답변에 동행자가 있으면 동행자도 참가자 한 행으로 만들어 대표자에 딸린다. 이번 문자에 담을 참가자는
 * 처음이면 대표자와 동행자 전원, 재신청이면 새 동행자만이다(새 동행자가 없으면 문자는 없고 응답에만 이번 일행을 담는다).
 * 재신청 응답의 일행에서는 취소된 동행자 중 이번에 다시 신청하지 않은 사람만 뺀다.
 * 같은 대표자 밑에서 이름·구분·소속이 모두 같은 동행자는 같은 사람이라 새로 만들지 않으며, 대표자를 포함한 인원은
 * 누적해서 최대 5명이다(요청이 알려 준 취소된 동행자는 뺀다). 기존 참가자의 `code`는 바뀌지 않는다.
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
        QuestionSnapshot.errorOf(reqDto.questions)?.let { throw ExpectedException(HttpStatus.BAD_REQUEST, it) }
        val companions = Companions.extract(reqDto.informationJson, reqDto.questions)
        if (1 + companions.size > Companions.MAX_PARTICIPANTS) {
            throw ExpectedException(HttpStatus.BAD_REQUEST, "대표자를 포함해 최대 ${Companions.MAX_PARTICIPANTS}명까지 신청할 수 있습니다.")
        }
        val digits = PhoneNumbers.digitsOnly(reqDto.phoneNumber)
        // 표기만 다른 같은 번호의 동시 등록도 하나씩 처리해야 아래 확인이 앞선 요청의 저장을 볼 수 있다
        registrationLock.lockPhone(reqDto.expoId, digits)

        val existing =
            standardParticipantRepository.findByExpoIdAndPhoneNumber(reqDto.expoId, reqDto.phoneNumber)
                ?: PhoneNumbers.select(standardParticipantRepository.findAllByExpoIdAndDigits(reqDto.expoId, digits), reqDto.phoneNumber) {
                    it.phoneNumber
                }
        if (existing == null) return registerNew(reqDto, companions)

        return registerAgain(reqDto, existing, companions)
    }

    private fun registerNew(
        reqDto: CreateStandardParticipantReqDto,
        companions: List<CompanionInfo>,
    ): CreateStandardParticipantResDto {
        val representative = save(newRepresentative(reqDto))
        val saved = listOf(representative) + companions.map { save(newCompanion(reqDto, representative.id!!, it)) }
        return CreateStandardParticipantResDto(
            participantId = representative.id!!,
            phoneNumber = representative.phoneNumber!!,
            created = true,
            participantIds = saved.map { it.id!! },
            groupParticipantIds = saved.map { it.id!! },
            createdIds = saved.map { it.id!! },
            participants = saved.map { RegisteredParticipant(it.id!!, it.code) },
        )
    }

    private fun registerAgain(
        reqDto: CreateStandardParticipantReqDto,
        representative: StandardParticipant,
        companions: List<CompanionInfo>,
    ): CreateStandardParticipantResDto {
        val registered = standardParticipantRepository.findAllByRepresentativeIdOrderById(representative.id!!)
        val known = registered.map { listOf(it.name, it.occupation, it.school) }.toSet()
        val added = companions.filter { it.key !in known }
        // 이번 요청 뒤에도 일행에 남는 기존 동행자(`live`)를 저장 전에 한 번 계산해 한도에 쓴다. 이번에 다시 신청한 사람은 되살아나 센다.
        // 그 외에는 이번 `releasedParticipantIds`에 없고 저장된 `released` 표시도 없어야 센다. 아래 표시 갱신 뒤의 `!released`와 같은
        // 집합이다. `registered`에는 대표자 본인이 없고 이 대표자의 동행자만 있으므로, 대표자 본인·다른 대표자의 ID·없는 ID는 무시된다
        val released = reqDto.releasedParticipantIds.orEmpty().toSet()
        val requested = companions.map { it.key }.toSet()
        val live = registered.filter { listOf(it.name, it.occupation, it.school) in requested || (it.id !in released && !it.released) }
        if (1 + live.size + added.size > Companions.MAX_PARTICIPANTS) {
            throw ExpectedException(HttpStatus.CONFLICT, "대표자를 포함해 최대 ${Companions.MAX_PARTICIPANTS}명까지 신청할 수 있습니다.")
        }
        val created = added.map { save(newCompanion(reqDto, representative.id!!, it)) }
        // 취소된 동행자 중 다시 신청하지 않은 사람은 `released`로 표시해 `managed` 목록에서 뺀다. 다시 신청한 사람은 되돌린다.
        // 요청에 `releasedParticipantIds`가 없으면 기존 표시를 건드리지 않는다
        registered.forEach {
            when {
                listOf(it.name, it.occupation, it.school) in requested -> it.changeReleased(false)
                it.id in released -> it.changeReleased(true)
            }
        }
        // 이번 일행: 대표자, 표시 갱신 뒤 `released`가 아닌 기존 동행자(위 `live`와 같은 집합), 새 동행자(`id` 순서)
        val group = listOf(representative) + registered.filterNot { it.released } + created
        // 응답의 `participantIds`: 새 동행자가 있으면 그들만(문자 대상), 없으면 이번 일행(문자는 보내지 않음). 일행의 부분집합이어야 한다
        val texted = if (created.isEmpty()) group else created
        return CreateStandardParticipantResDto(
            participantId = representative.id!!,
            phoneNumber = representative.phoneNumber!!,
            created = created.isNotEmpty(),
            participantIds = texted.map { it.id!! },
            // 문자 대상과 달리 새 동행자만 있어도 일행 전체다
            groupParticipantIds = group.map { it.id!! },
            createdIds = created.map { it.id!! },
            participants = texted.map { RegisteredParticipant(it.id!!, it.code) },
            // 새 동행자가 없으면 기존 QR을 그대로 쓰므로 문자를 보내지 않는다. `participantIds`는 그대로 이번 일행을 담는다
            smsSkipped = created.isEmpty(),
        )
    }

    private fun newRepresentative(reqDto: CreateStandardParticipantReqDto) =
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
            region = reqDto.region,
            informationFormId = reqDto.formId,
            informationQuestions = QuestionSnapshot.serialize(reqDto.questions),
        )

    // 동행자는 번호가 없고 폼 답변은 대표자의 신청에 담겨 있으므로 이름·구분·지역·소속만 갖는다
    private fun newCompanion(
        reqDto: CreateStandardParticipantReqDto,
        representativeId: Long,
        companion: CompanionInfo,
    ) = StandardParticipant(
        expoId = reqDto.expoId,
        name = companion.name,
        phoneNumber = null,
        personalInformationStatus = reqDto.personalInformationStatus,
        applicationType = reqDto.applicationType,
        applicationDate = LocalDateTime.now(),
        occupation = companion.occupation,
        school = companion.school,
        region = companion.region,
        representativeId = representativeId,
    )

    private fun save(participant: StandardParticipant): StandardParticipant =
        try {
            standardParticipantRepository.save(participant)
        } catch (exception: DataIntegrityViolationException) {
            throw ConstraintViolations.translate(exception)
        }
}
