package team.startup.expo.domain.user.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.user.presentation.dto.response.GetMyInformationResDto
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.domain.user.repository.getProfileById
import team.startup.expo.domain.user.service.GetMyInformationService

@Service
class GetMyInformationServiceImpl(
    private val adminRepository: AdminRepository,
) : GetMyInformationService {
    @Transactional(readOnly = true)
    override fun execute(adminId: Long): GetMyInformationResDto {
        val profile = adminRepository.getProfileById(adminId)
        return GetMyInformationResDto(
            name = profile.name,
            nickname = profile.nickname,
            email = profile.email,
            superAdmin = profile.superAdmin,
        )
    }
}
