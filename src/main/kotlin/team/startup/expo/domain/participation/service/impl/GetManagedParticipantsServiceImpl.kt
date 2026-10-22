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
 * 취소 이력과 재등록 ID 선택을 위해 비활성 참가자도 보존한다. 입장·프로그램 자격과 활성 인원은 이 목록으로 판단하지 않는다.
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
                .findAllByIdOrRepresentativeIdOrderById(representativeId, representativeId)
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
