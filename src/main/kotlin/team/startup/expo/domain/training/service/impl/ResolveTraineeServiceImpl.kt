package team.startup.expo.domain.training.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.training.presentation.dto.request.ResolveTraineeReqDto
import team.startup.expo.domain.training.presentation.dto.response.ResolveTraineeResDto
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.domain.training.service.ResolveTraineeService
import team.startup.expo.global.exception.ExpectedException

@Service
class ResolveTraineeServiceImpl(
    private val traineeRepository: TraineeRepository,
) : ResolveTraineeService {
    /**
     * 박람회와 연수 번호로 연수자 한 명을 찾는다. `(expo_id, training_id)`에는 유일 제약이 없어 같은 번호의
     * 연수자가 여럿일 수 있는데, 이때 임의의 한 명을 성공으로 돌려주면 다른 사람의 프로그램 신청이 되므로 409로 실패한다.
     */
    @Transactional(readOnly = true)
    override fun execute(reqDto: ResolveTraineeReqDto): ResolveTraineeResDto {
        val trainees = traineeRepository.findAllByExpoIdAndTrainingId(reqDto.expoId, reqDto.trainingId)
        return when (trainees.size) {
            0 -> throw ExpectedException(HttpStatus.NOT_FOUND, "연수자를 찾지 못 했습니다.")
            1 -> ResolveTraineeResDto(traineeId = trainees.single().id)
            else -> throw ExpectedException(HttpStatus.CONFLICT, "같은 연수 번호의 연수자가 여러 명이라 하나로 특정할 수 없습니다.")
        }
    }
}
