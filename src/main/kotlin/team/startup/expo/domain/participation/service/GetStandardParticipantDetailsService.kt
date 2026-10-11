package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.GetStandardParticipantBriefsReqDto
import team.startup.expo.domain.participation.presentation.dto.request.StandardParticipantDetailPageReqDto
import team.startup.expo.domain.participation.presentation.dto.response.StandardParticipantBriefResDto
import team.startup.expo.domain.participation.presentation.dto.response.StandardParticipantDetailResDto
import team.startup.expo.global.dto.DetailPageResDto

interface GetStandardParticipantDetailsService {
    fun page(
        expoId: String,
        reqDto: StandardParticipantDetailPageReqDto,
    ): DetailPageResDto<StandardParticipantDetailResDto>

    fun briefs(reqDto: GetStandardParticipantBriefsReqDto): List<StandardParticipantBriefResDto>
}
