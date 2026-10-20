package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.GetRegistrationStatusReqDto
import team.startup.expo.domain.participation.presentation.dto.response.RegistrationStatusResDto

interface GetRegistrationStatusService {
    fun execute(reqDto: GetRegistrationStatusReqDto): RegistrationStatusResDto
}
