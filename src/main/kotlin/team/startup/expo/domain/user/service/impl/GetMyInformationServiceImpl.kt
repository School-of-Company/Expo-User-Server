package team.startup.expo.domain.user.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.user.presentation.dto.response.GetMyInformationResDto
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.domain.user.service.GetMyInformationService
import team.startup.expo.global.exception.ExpectedException

@Service
class GetMyInformationServiceImpl(
    private val adminRepository: AdminRepository,
) : GetMyInformationService {
    @Transactional(readOnly = true)
    override fun execute(adminId: Long): GetMyInformationResDto {
        val admin =
            adminRepository.findById(adminId).orElse(null)
                ?: throw ExpectedException(HttpStatus.NOT_FOUND, "해당 유저를 찾을 수 없습니다.")
        return GetMyInformationResDto(name = admin.name, nickname = admin.nickname, email = admin.email)
    }
}
