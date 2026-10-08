package team.startup.expo.domain.user.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.domain.user.service.AcceptAdminService
import team.startup.expo.global.exception.ExpectedException

@Service
class AcceptAdminServiceImpl(
    private val adminRepository: AdminRepository,
) : AcceptAdminService {
    @Transactional
    override fun execute(adminId: Long) {
        val admin =
            adminRepository.findByIdForUpdate(adminId)
                ?: throw ExpectedException(HttpStatus.NOT_FOUND, "해당 유저를 찾을 수 없습니다.")
        admin.accept()
    }
}
