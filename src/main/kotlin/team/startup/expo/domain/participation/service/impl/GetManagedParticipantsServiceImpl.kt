package team.startup.expo.domain.participation.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.presentation.dto.request.GetManagedParticipantsReqDto
import team.startup.expo.domain.participation.presentation.dto.response.GetManagedParticipantsResDto
import team.startup.expo.domain.participation.presentation.dto.response.ManagedParticipantResDto
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.GetManagedParticipantsService
import team.startup.expo.domain.participation.service.RepresentativeFinder

/**
 * 검증된 전화번호의 대표자와 그 동행자를 `id` 순서로 돌려준다(대표자가 먼저다). 이 번호로 등록된 대표자가 없으면 오류가 아니라
 * `registered = false`로 구분해 신청 서비스가 먼저 등록하게 한다. 다른 대표자의 동행자는 섞이지 않는다.
 * 신청이 취소된 뒤 다시 신청하지 않은 동행자(`released`)는 싣지 않는다. 그 행이 남아 있어도 신청 서비스가 보는 일행은 누적 5명을 넘지 않는다.
 */
@Service
class GetManagedParticipantsServiceImpl(
    private val representativeFinder: RepresentativeFinder,
    private val standardParticipantRepository: StandardParticipantRepository,
) : GetManagedParticipantsService {
    @Transactional(readOnly = true)
    override fun execute(reqDto: GetManagedParticipantsReqDto): GetManagedParticipantsResDto {
        val representative =
            representativeFinder.find(reqDto.expoId, reqDto.phoneNumber)
                ?: return GetManagedParticipantsResDto(registered = false, participants = emptyList())
        val representativeId = requireNotNull(representative.id)
        val participants =
            standardParticipantRepository
                .findManagedByRepresentative(representativeId)
                .map {
                    ManagedParticipantResDto(
                        it.id,
                        it.name,
                        it.occupation,
                        representative = it.id == representativeId,
                        applicationType = it.applicationType,
                    )
                }
        return GetManagedParticipantsResDto(registered = true, participants = participants)
    }
}
