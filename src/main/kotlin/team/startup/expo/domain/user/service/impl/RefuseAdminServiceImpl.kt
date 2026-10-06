package team.startup.expo.domain.user.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.user.entity.Status
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.domain.user.service.RefuseAdminService
import team.startup.expo.global.exception.ExpectedException

@Service
class RefuseAdminServiceImpl(
    private val adminRepository: AdminRepository,
) : RefuseAdminService {
    @Transactional
    override fun execute(adminId: Long) {
        val admin =
            adminRepository.findById(adminId).orElse(null)
                ?: throw ExpectedException(HttpStatus.NOT_FOUND, "해당 유저를 찾을 수 없습니다.")
        if (admin.status != Status.PENDING) {
            throw ExpectedException(HttpStatus.CONFLICT, "이미 수락한 유저입니다.")
        }
        adminRepository.delete(admin)
    }
}
