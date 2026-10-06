package team.startup.expo.domain.user.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.entity.Status
import team.startup.expo.domain.user.presentation.dto.response.GetPendingAdminResDto
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.domain.user.service.GetPendingAdminsService

@Service
class GetPendingAdminsServiceImpl(
    private val adminRepository: AdminRepository,
) : GetPendingAdminsService {
    @Transactional(readOnly = true)
    override fun execute(): List<GetPendingAdminResDto> = adminRepository.findByStatus(Status.PENDING).map { it.toResDto() }

    private fun Admin.toResDto() =
        GetPendingAdminResDto(
            id = requireNotNull(id),
            name = name,
            nickname = nickname,
            email = email,
            phoneNumber = phoneNumber,
        )
}
