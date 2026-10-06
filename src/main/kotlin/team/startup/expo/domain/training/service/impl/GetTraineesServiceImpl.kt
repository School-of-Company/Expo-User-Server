package team.startup.expo.domain.training.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.training.presentation.dto.request.GetTraineeReqDto
import team.startup.expo.domain.training.presentation.dto.response.GetTraineeResDto
import team.startup.expo.domain.training.repository.TraineeInfoView
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.domain.training.service.GetTraineesService
import team.startup.expo.global.client.expo.ExpoPeriodReader
import team.startup.expo.global.exception.ExpectedException

@Service
class GetTraineesServiceImpl(
    private val traineeRepository: TraineeRepository,
    private val expoPeriodReader: ExpoPeriodReader,
) : GetTraineesService {
    /**
     * 노션 명세에서 "나중에 N+1 문제 해결"로 남겨 둔 목록이다. 이 서비스의 `Trainee`는 박람회를 `expo_id`
     * 문자열로만 가지고 연관 엔티티가 없어서 N+1이 생길 곳이 없고, 목록에 필요한 컬럼만 투영으로 읽어
     * 쿼리 한 번으로 끝낸다.
     */
    @Transactional(readOnly = true)
    override fun execute(
        expoId: String,
        reqDto: GetTraineeReqDto,
    ): List<GetTraineeResDto> {
        expoPeriodReader.find(expoId) ?: throw ExpectedException(HttpStatus.NOT_FOUND, "박람회를 찾지 못 했습니다.")

        val name = reqDto.name?.takeIf { it.isNotBlank() }
        val trainees =
            if (name == null) {
                traineeRepository.findByExpoIdOrderById(expoId)
            } else {
                traineeRepository.findByExpoIdAndNameContainingOrderById(expoId, name)
            }
        return trainees.map { it.toResDto() }
    }

    private fun TraineeInfoView.toResDto() =
        GetTraineeResDto(
            id = id,
            name = name,
            trainingId = trainingId,
            phoneNumber = phoneNumber,
            applicationType = applicationType,
        )
}
