package team.startup.expo.domain.participation.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.presentation.dto.request.SelectManagedParticipantsReqDto
import team.startup.expo.domain.participation.presentation.dto.response.ManagedParticipantResDto
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.RepresentativeFinder
import team.startup.expo.domain.participation.service.SelectManagedParticipantsService
import team.startup.expo.global.exception.ExpectedException

/**
 * 신청 서비스가 선택한 참가자가 그 번호의 대표자 본인이나 동행자인지 확인하고, 저장된 값을 돌려준다. 요청한 `id`를 모두 요청
 * 순서대로 돌려주거나, 하나라도 내 관리 대상이 아니면(다른 대표자의 참가자, 다른 행사, 없는 `id`, 등록되지 않은 번호) 구분하지
 * 않고 같은 `404`로 실패한다. 남의 참가자 `id`가 존재하는지 드러나지 않게 하고, 일부만 돌려주지도 않는다.
 */
@Service
class SelectManagedParticipantsServiceImpl(
    private val representativeFinder: RepresentativeFinder,
    private val standardParticipantRepository: StandardParticipantRepository,
) : SelectManagedParticipantsService {
    @Transactional(readOnly = true)
    override fun execute(reqDto: SelectManagedParticipantsReqDto): List<ManagedParticipantResDto> {
        val representative = representativeFinder.find(reqDto.expoId, reqDto.phoneNumber) ?: throw notFound()
        val representativeId = requireNotNull(representative.id)
        val managed =
            standardParticipantRepository
                .findAllByIdOrRepresentativeIdOrderById(representativeId, representativeId)
                .associateBy { it.id }
        val ids = reqDto.participantIds.distinct()
        if (!ids.all(managed::containsKey)) throw notFound()
        return ids.map {
            val view = managed.getValue(it)
            ManagedParticipantResDto(
                view.id,
                view.name,
                view.occupation,
                representative = view.id == representativeId,
                applicationType = view.applicationType,
            )
        }
    }

    private fun notFound() = ExpectedException(HttpStatus.NOT_FOUND, "관리하는 참가자를 찾을 수 없습니다.")
}
