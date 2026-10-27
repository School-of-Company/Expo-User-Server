package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.GetEntryCardsReqDto
import team.startup.expo.domain.participation.presentation.dto.response.EntryCardsResDto

interface GetEntryCardsService {
    fun execute(reqDto: GetEntryCardsReqDto): EntryCardsResDto
}
