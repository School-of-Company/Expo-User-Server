package team.startup.expo.domain.training.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.training.presentation.dto.request.GetTraineeNamesReqDto
import team.startup.expo.domain.training.presentation.dto.response.TraineeNameResDto
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.domain.training.service.GetTraineeNamesService
import team.startup.expo.global.exception.ExpectedException

@Service
class GetTraineeNamesServiceImpl(
    private val traineeRepository: TraineeRepository,
) : GetTraineeNamesService {
    /**
     * 요청한 id를 모두 돌려주거나 아예 실패한다. 없는 id와 다른 박람회의 연수자를 구분하지 않고 같은 404로 처리해
     * 다른 박람회의 연수자 존재 여부가 드러나지 않게 한다. 실패할 때는 호출자가 보낸 id 중 찾지 못한 것(최대 20개)을
     * 알려 준다. 중복 id는 한 번만 반환하고 순서는 요청 순서를 따른다.
     */
    @Transactional(readOnly = true)
    override fun execute(reqDto: GetTraineeNamesReqDto): List<TraineeNameResDto> {
        val ids = reqDto.traineeIds.distinct()
        // id가 많아도 IN 절이 한없이 길어지지 않도록 나눠서 조회한다. 이름만 필요하므로 투영으로 읽는다
        val names =
            ids
                .chunked(QUERY_CHUNK_SIZE)
                .flatMap { traineeRepository.findNamesByExpoIdAndIdIn(reqDto.expoId, it) }
                .associate { it.id to it.name }
        if (names.size != ids.size) {
            val missing = ids.filterNot(names::containsKey)
            val shown = missing.take(MAX_REPORTED_IDS).joinToString(", ")
            val rest = if (missing.size > MAX_REPORTED_IDS) " 외 ${missing.size - MAX_REPORTED_IDS}개" else ""
            throw ExpectedException(HttpStatus.NOT_FOUND, "요청한 연수자 중 찾을 수 없는 연수자가 있습니다. (id: $shown$rest)")
        }
        return ids.map { TraineeNameResDto(traineeId = it, name = names.getValue(it)) }
    }

    private companion object {
        const val QUERY_CHUNK_SIZE = 1_000
        const val MAX_REPORTED_IDS = 20
    }
}
