package team.startup.expo.domain.participation.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.presentation.dto.request.GetStandardParticipantNamesReqDto
import team.startup.expo.domain.participation.presentation.dto.response.StandardParticipantNameResDto
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.GetStandardParticipantNamesService
import team.startup.expo.global.exception.ExpectedException

@Service
class GetStandardParticipantNamesServiceImpl(
    private val standardParticipantRepository: StandardParticipantRepository,
) : GetStandardParticipantNamesService {
    /**
     * 요청한 id를 모두 돌려주거나 아예 실패한다. 없는 id와 다른 박람회의 참가자를 구분하지 않고 같은 404로
     * 처리해, 다른 박람회의 참가자 존재 여부가 드러나지 않게 한다. 중복 id는 한 번만 반환하고 순서는
     * 요청 순서를 따른다.
     */
    @Transactional(readOnly = true)
    override fun execute(reqDto: GetStandardParticipantNamesReqDto): List<StandardParticipantNameResDto> {
        val ids = reqDto.participantIds.distinct()
        // id가 많아도 IN 절이 한없이 길어지지 않도록 나눠서 조회한다
        val found =
            ids
                .chunked(QUERY_CHUNK_SIZE)
                .flatMap { standardParticipantRepository.findAllByExpoIdAndIdIn(reqDto.expoId, it) }
                .associateBy { requireNotNull(it.id) }
        if (found.size != ids.size) {
            throw ExpectedException(HttpStatus.NOT_FOUND, "요청한 참가자 중 찾을 수 없는 참가자가 있습니다.")
        }
        return ids.map { StandardParticipantNameResDto(participantId = it, name = found.getValue(it).name) }
    }

    private companion object {
        const val QUERY_CHUNK_SIZE = 1_000
    }
}
