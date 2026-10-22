package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.GetEntryStatusReqDto
import team.startup.expo.domain.participation.presentation.dto.response.EntryStatusResDto

interface GetEntryStatusService {
    fun execute(body: GetEntryStatusReqDto): EntryStatusResDto
}
