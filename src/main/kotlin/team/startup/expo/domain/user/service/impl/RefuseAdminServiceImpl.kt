package team.startup.expo.domain.user.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.user.entity.Status
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.domain.user.repository.getByIdForUpdate
import team.startup.expo.domain.user.service.RefuseAdminService
import team.startup.expo.global.exception.ExpectedException

@Service
class RefuseAdminServiceImpl(
    private val adminRepository: AdminRepository,
) : RefuseAdminService {
    @Transactional
    override fun execute(adminId: Long) {
        // 행을 잠가 둔다. 확인한 뒤 다른 관리자가 승인을 마치면 승인된 계정을 삭제하게 되기 때문이다.
        val admin = adminRepository.getByIdForUpdate(adminId)
        if (admin.status != Status.PENDING) {
            throw ExpectedException(HttpStatus.CONFLICT, "이미 수락한 유저입니다.")
        }
        adminRepository.delete(admin)
    }
}
