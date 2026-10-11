package team.startup.expo.domain.training.service.impl

import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.repository.ParticipantRegistrationView
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.training.entity.Trainee
import team.startup.expo.domain.training.presentation.dto.response.TraineeDetailItemResDto
import team.startup.expo.domain.training.presentation.dto.response.TraineeDetailResDto
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.domain.training.service.GetTraineeDetailsService
import team.startup.expo.global.dto.DetailPageReqDto
import team.startup.expo.global.dto.DetailPageResDto
import team.startup.expo.global.dto.InformationResDto
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.util.InformationJson

/**
 * Report 서비스의 엑셀 내보내기용 조회다. v1 `findByExpo`처럼 `id` 오름차순이고, 행사에 연수자가 없으면 빈 목록이다.
 * 행사 존재 여부는 호출자가 Expo 서비스에 따로 확인한다.
 *
 * 사전등록 참가자에 이어진 연수자는 연결된 참가자의 등록 답변 원천을 `registration`으로 함께 돌려준다. 연수자 자신의
 * `information`과 섞지 않고, 신규 연수 신청 답변은 신청 서비스 소유라 여기에 없다. 스냅샷 없이 저장된 참가자(동행자)나
 * 다른 박람회의 참가자는 `registration`이 `null`이다. 페이지의 연결된 참가자는 한 번에 읽는다.
 */
@Service
class GetTraineeDetailsServiceImpl(
    private val traineeRepository: TraineeRepository,
    private val standardParticipantRepository: StandardParticipantRepository,
) : GetTraineeDetailsService {
    @Transactional(readOnly = true)
    override fun page(
        expoId: String,
        reqDto: DetailPageReqDto,
    ): DetailPageResDto<TraineeDetailItemResDto> {
        val size = reqDto.sizeOrDefault
        // 다음 페이지가 있는지 알려고 한 행 더 읽는다
        val rows = traineeRepository.findByExpoIdAndIdGreaterThanOrderById(expoId, reqDto.cursorOrZero, PageRequest.ofSize(size + 1))
        val hasNext = rows.size > size
        val pageRows = rows.take(size)
        val registrations = registrationsOf(pageRows)
        val items =
            pageRows.map {
                TraineeDetailItemResDto(
                    traineeId = it.id!!,
                    name = it.name,
                    trainingId = it.trainingId,
                    phoneNumber = it.phoneNumber,
                    notificationPhoneNumber = it.notificationPhoneNumber ?: it.phoneNumber,
                    personalInformationStatus = it.personalInformationStatus,
                    applicationType = it.applicationType,
                    information =
                        InformationResDto(
                            answers = InformationJson.toNode(it.informationJson),
                            questions = InformationJson.toQuestionsNode(it.informationQuestions),
                        ),
                    participantId = it.standardParticipantId,
                    school = it.school,
                    registration = registrationOf(it, registrations),
                )
            }
        return DetailPageResDto(items = items, nextCursor = if (hasNext) items.last().traineeId else null)
    }

    @Transactional(readOnly = true)
    override fun one(traineeId: Long): TraineeDetailResDto {
        val trainee =
            traineeRepository.findByIdOrNull(traineeId)
                ?: throw ExpectedException(HttpStatus.NOT_FOUND, "연수자를 찾지 못 했습니다.")
        return TraineeDetailResDto(
            traineeId = trainee.id!!,
            expoId = trainee.expoId,
            name = trainee.name,
            trainingId = trainee.trainingId,
            information =
                InformationResDto(
                    answers = InformationJson.toNode(trainee.informationJson),
                    questions = InformationJson.toQuestionsNode(trainee.informationQuestions),
                ),
            participantId = trainee.standardParticipantId,
            school = trainee.school,
            registration = registrationOf(trainee, registrationsOf(listOf(trainee))),
        )
    }

    /** 이어진 참가자의 등록 답변 원천을 `id`별로 한 번에 읽는다. 이어진 연수자가 없으면 읽지 않는다. */
    private fun registrationsOf(trainees: List<Trainee>): Map<Long, ParticipantRegistrationView> {
        val participantIds = trainees.mapNotNull { it.standardParticipantId }.distinct()
        if (participantIds.isEmpty()) return emptyMap()
        return participantIds
            .chunked(QUERY_CHUNK_SIZE)
            .flatMap { standardParticipantRepository.findRegistrationsByIdIn(it) }
            .associateBy { it.id }
    }

    private fun registrationOf(
        trainee: Trainee,
        registrations: Map<Long, ParticipantRegistrationView>,
    ): InformationResDto? {
        val source = trainee.standardParticipantId?.let(registrations::get) ?: return null
        // 연수자와 같은 박람회의 참가자만 원천으로 쓴다
        if (source.expoId != trainee.expoId) return null
        // 스냅샷도 답변도 없이 저장된 행(동행자 등)은 복원할 원천이 없다
        if (source.informationJson == null && source.informationQuestions == null) return null
        return InformationResDto(
            answers = InformationJson.toNode(source.informationJson),
            questions = InformationJson.toQuestionsNode(source.informationQuestions),
        )
    }

    private companion object {
        const val QUERY_CHUNK_SIZE = 1_000
    }
}
