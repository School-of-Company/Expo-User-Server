package team.startup.expo.domain.user.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.domain.user.repository.getByIdForUpdate
import team.startup.expo.domain.user.service.AcceptAdminService

@Service
class AcceptAdminServiceImpl(
    private val adminRepository: AdminRepository,
) : AcceptAdminService {
    @Transactional
    override fun execute(adminId: Long) {
        adminRepository.getByIdForUpdate(adminId).accept()
    }
}
