package team.startup.expo.domain.training.service.impl

import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
 */
@Service
class GetTraineeDetailsServiceImpl(
    private val traineeRepository: TraineeRepository,
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
        val items =
            rows.take(size).map {
                TraineeDetailItemResDto(
                    traineeId = it.id!!,
                    name = it.name,
                    trainingId = it.trainingId,
                    phoneNumber = it.phoneNumber,
                    personalInformationStatus = it.personalInformationStatus,
                    applicationType = it.applicationType,
                    information = InformationResDto(answers = InformationJson.toNode(it.informationJson)),
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
            information = InformationResDto(answers = InformationJson.toNode(trainee.informationJson)),
        )
    }
}
