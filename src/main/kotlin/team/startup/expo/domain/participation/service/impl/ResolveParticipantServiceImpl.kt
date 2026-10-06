package team.startup.expo.domain.participation.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.entity.ParticipationType
import team.startup.expo.domain.participation.presentation.dto.request.ResolveParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.response.ResolveParticipantResDto
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.ResolveParticipantService
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.util.PhoneNumbers

/**
 * 같은 번호가 같은 박람회에 연수자와 일반 참가자 양쪽으로 신청할 수 있으므로(`(expo_id, phone_number)`
 * UNIQUE는 테이블별이다) 호출자가 응답자 구분을 지정하고, 그 구분의 테이블만 조회한다.
 *
 * 저장된 표기와 정확히 같은 번호를 인덱스로 먼저 찾고, 없을 때만 숫자만 남겨 비교한다.
 */
@Service
class ResolveParticipantServiceImpl(
    private val standardParticipantRepository: StandardParticipantRepository,
    private val traineeRepository: TraineeRepository,
) : ResolveParticipantService {
    @Transactional(readOnly = true)
    override fun execute(reqDto: ResolveParticipantReqDto): ResolveParticipantResDto {
        val digits = PhoneNumbers.digitsOnly(reqDto.phoneNumber)
        val participantId =
            when (reqDto.participationType) {
                ParticipationType.STANDARD -> {
                    standardParticipantRepository.findByExpoIdAndPhoneNumber(reqDto.expoId, reqDto.phoneNumber)?.id
                        ?: PhoneNumbers
                            .select(standardParticipantRepository.findAllByExpoIdAndDigits(reqDto.expoId, digits), reqDto.phoneNumber) {
                                it.phoneNumber
                            }?.id
                }

                ParticipationType.TRAINEE -> {
                    traineeRepository.findByExpoIdAndPhoneNumber(reqDto.expoId, reqDto.phoneNumber)?.id
                        ?: PhoneNumbers
                            .select(traineeRepository.findAllByExpoIdAndDigits(reqDto.expoId, digits), reqDto.phoneNumber) {
                                it.phoneNumber
                            }?.id
                }
            } ?: throw ExpectedException(HttpStatus.NOT_FOUND, "행사 참가자를 찾지 못 했습니다.")
        return ResolveParticipantResDto(participantId = participantId, participationType = reqDto.participationType)
    }
}
