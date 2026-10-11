package team.startup.expo.domain.participation.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.presentation.dto.request.ResolveTraineeByParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.response.ResolveTraineeByParticipantResDto
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.ResolveTraineeByParticipantService
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.util.ParticipantCode

/**
 * 참여 서비스가 연수 출석을 사전등록 참가자 QR(`participantId`와 `code`)로 찍을 때, 그 참가자에 이어진 연수자를 찾는다. 입장이나
 * 출석을 기록하지 않고 아무것도 바꾸지 않으며, 이어진 연수자가 없어도 새로 만들지 않는다.
 *
 * 참가자가 없거나, 다른 박람회의 참가자이거나, `code`가 다르거나, 이어진 연수자가 없으면 구분하지 않고 같은 404로 답해 남의
 * 참가자 ID가 있는지, 연수자와 이어져 있는지 드러나지 않게 한다.
 */
@Service
class ResolveTraineeByParticipantServiceImpl(
    private val standardParticipantRepository: StandardParticipantRepository,
    private val traineeRepository: TraineeRepository,
) : ResolveTraineeByParticipantService {
    @Transactional(readOnly = true)
    override fun execute(reqDto: ResolveTraineeByParticipantReqDto): ResolveTraineeByParticipantResDto {
        val participant =
            standardParticipantRepository
                .findByIdAndExpoId(requireNotNull(reqDto.participantId), reqDto.expoId)
                ?.takeIf { ParticipantCode.matches(it.code, reqDto.code) }
                ?: throw notFound()
        val trainee = traineeRepository.findByStandardParticipantId(requireNotNull(participant.id)) ?: throw notFound()
        return ResolveTraineeByParticipantResDto(requireNotNull(trainee.id))
    }

    private fun notFound() = ExpectedException(HttpStatus.NOT_FOUND, "행사 참가자를 찾지 못 했습니다.")
}
