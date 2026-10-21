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
import team.startup.expo.domain.training.entity.ApplicationType
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
 *
 * 요청의 `replaceRepresentative`가 true이고 대표자가 있으면 재신청 대신 대표자를 교체한다([replace]).
 *
 * 사전등록(PRE) 대표자의 번호로 현장등록(FIELD) 요청이 오면 신청 서비스가 현장등록을 허용한 재등록이다. 재신청 규칙은 그대로 두고,
 * 대표자의 신청 구분을 FIELD로 바꾸며 등록 정보(이름·직업·소속·지역·답변)를 이번 요청 값으로 갱신한다. 이번에 다시 신청한 기존 동행자도
 * FIELD로 바꾼다. 문자는 새 동행자가 없어도 보내며, 대상은 대표자·다시 신청한 동행자·새 동행자다.
 *
 * 대표자가 이미 FIELD여도 FIELD 요청에 다시 담긴 PRE 동행자는 FIELD로 바꾼다(#195). 이때 문자 대상은 이번에 FIELD가 된 동행자와 새 동행자다.
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
        if (reqDto.replaceRepresentative == true) return replace(reqDto, existing, companions)

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
        val field = reqDto.applicationType == ApplicationType.FIELD
        val toField = field && representative.applicationType == ApplicationType.PRE
        val resubmitted = registered.filter { listOf(it.name, it.occupation, it.school) in requested }
        // 대표자의 이전 구분과 무관하게, FIELD 요청에 다시 담긴 PRE 동행자만 FIELD로 바꾼다. 요청에 없는 동행자는 그대로 둔다
        val fielded = if (field) resubmitted.filter { it.applicationType == ApplicationType.PRE } else emptyList()
        if (toField) switchToField(reqDto, representative)
        fielded.forEach { it.applicationType = ApplicationType.FIELD }
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
        // 응답의 `participantIds`: FIELD 전환이면 이번에 FIELD가 된 사람(대표자 전환이면 대표자와 다시 신청한 동행자), 새 동행자가 있으면 그들만(문자 대상), 없으면 이번 일행(문자는
        // 보내지 않음). 일행의 부분집합이어야 한다
        val texted =
            when {
                toField -> listOf(representative) + resubmitted + created
                fielded.isNotEmpty() -> fielded + created
                created.isEmpty() -> group
                else -> created
            }
        return CreateStandardParticipantResDto(
            participantId = representative.id!!,
            phoneNumber = representative.phoneNumber!!,
            created = created.isNotEmpty(),
            participantIds = texted.map { it.id!! },
            // 문자 대상과 달리 새 동행자만 있어도 일행 전체다
            groupParticipantIds = group.map { it.id!! },
            createdIds = created.map { it.id!! },
            participants = texted.map { RegisteredParticipant(it.id!!, it.code) },
            // 새 동행자가 없으면 기존 QR을 그대로 쓰므로 문자를 보내지 않는다. `participantIds`는 그대로 이번 일행을 담는다.
            // FIELD 전환은 현장 입장용 QR 문자를 보낸다
            smsSkipped = created.isEmpty() && !toField && fielded.isEmpty(),
        )
    }

    private fun switchToField(
        reqDto: CreateStandardParticipantReqDto,
        representative: StandardParticipant,
    ) {
        representative.replace(
            name = reqDto.name,
            applicationType = ApplicationType.FIELD,
            occupation = reqDto.occupation,
            school = reqDto.school,
            region = reqDto.region,
            informationJson = reqDto.informationJson,
            informationFormId = reqDto.formId,
            informationQuestions = QuestionSnapshot.serialize(reqDto.questions),
        )
    }

    /**
     * 대표자의 신청이 모두 취소된 뒤의 새 신청이다. 대표자 행(`id`·`code`)은 그대로 두고 신청 정보만 이번 요청으로 바꾸며,
     * 기존 동행자는 모두 `released`로 표시한다. 요청의 동행자 중 같은 키의 기존 동행자는 되살리고 나머지는 새로 만든다.
     * 일행은 요청 인원뿐이라 한도는 위의 요청 인원 확인으로 충분하다. 문자는 이번 일행 전체에 보낸다.
     */
    private fun replace(
        reqDto: CreateStandardParticipantReqDto,
        representative: StandardParticipant,
        companions: List<CompanionInfo>,
    ): CreateStandardParticipantResDto {
        representative.replace(
            name = reqDto.name,
            applicationType = reqDto.applicationType,
            occupation = reqDto.occupation,
            school = reqDto.school,
            region = reqDto.region,
            informationJson = reqDto.informationJson,
            informationFormId = reqDto.formId,
            informationQuestions = QuestionSnapshot.serialize(reqDto.questions),
        )
        val registered = standardParticipantRepository.findAllByRepresentativeIdOrderById(representative.id!!)
        val requested = companions.map { it.key }.toSet()
        registered.forEach { it.changeReleased(listOf(it.name, it.occupation, it.school) !in requested) }
        val known = registered.map { listOf(it.name, it.occupation, it.school) }.toSet()
        val created = companions.filter { it.key !in known }.map { save(newCompanion(reqDto, representative.id!!, it)) }
        val group = listOf(representative) + registered.filterNot { it.released } + created
        return CreateStandardParticipantResDto(
            participantId = representative.id!!,
            phoneNumber = representative.phoneNumber!!,
            created = created.isNotEmpty(),
            participantIds = group.map { it.id!! },
            groupParticipantIds = group.map { it.id!! },
            createdIds = created.map { it.id!! },
            participants = group.map { RegisteredParticipant(it.id!!, it.code) },
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
