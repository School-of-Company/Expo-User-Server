package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.RecordEntryReqDto
import team.startup.expo.domain.participation.presentation.dto.response.RecordEntryResDto

interface RecordEntryService {
    fun execute(reqDto: RecordEntryReqDto): RecordEntryResDto
}
