package team.startup.expo.domain.user.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.user.entity.Status
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.domain.user.repository.getByIdForUpdate
import team.startup.expo.domain.user.service.AcceptAdminService
import team.startup.expo.global.exception.ExpectedException

@Service
class AcceptAdminServiceImpl(
    private val adminRepository: AdminRepository,
) : AcceptAdminService {
    @Transactional
    override fun execute(adminId: Long) {
        val admin = adminRepository.getByIdForUpdate(adminId)
        if (admin.status != Status.PENDING) {
            throw ExpectedException(HttpStatus.CONFLICT, "이미 수락한 유저입니다.")
        }
        admin.accept()
    }
}
