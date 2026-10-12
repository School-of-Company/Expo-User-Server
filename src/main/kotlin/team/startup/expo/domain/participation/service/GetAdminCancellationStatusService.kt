package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.GetAdminCancellationStatusReqDto
import team.startup.expo.domain.participation.presentation.dto.response.AdminCancellationStatusResDto

interface GetAdminCancellationStatusService {
    fun execute(reqDto: GetAdminCancellationStatusReqDto): AdminCancellationStatusResDto
}
