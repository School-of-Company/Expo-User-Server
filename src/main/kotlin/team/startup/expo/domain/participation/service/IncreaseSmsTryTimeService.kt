package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.IncreaseSmsTryTimeReqDto

interface IncreaseSmsTryTimeService {
    fun execute(reqDto: IncreaseSmsTryTimeReqDto)
}
