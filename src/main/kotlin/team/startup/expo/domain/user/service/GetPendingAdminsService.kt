package team.startup.expo.domain.user.service

import team.startup.expo.domain.user.presentation.dto.response.GetPendingAdminResDto

interface GetPendingAdminsService {
    fun execute(): List<GetPendingAdminResDto>
}
