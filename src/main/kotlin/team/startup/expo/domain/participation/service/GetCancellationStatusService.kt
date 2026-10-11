package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.GetCancellationStatusReqDto
import team.startup.expo.domain.participation.presentation.dto.response.CancellationStatusResDto

interface GetCancellationStatusService {
    fun execute(reqDto: GetCancellationStatusReqDto): CancellationStatusResDto
}
