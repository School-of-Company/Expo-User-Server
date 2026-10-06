package team.startup.expo.domain.user.service

import team.startup.expo.domain.user.presentation.dto.response.GetMyInformationResDto

interface GetMyInformationService {
    fun execute(adminId: Long): GetMyInformationResDto
}
