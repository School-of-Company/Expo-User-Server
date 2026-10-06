package team.startup.expo.domain.user.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.auth.service.RefreshTokenService
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.domain.user.service.WithdrawalAdminService

@Service
class WithdrawalAdminServiceImpl(
    private val adminRepository: AdminRepository,
    private val refreshTokenService: RefreshTokenService,
) : WithdrawalAdminService {
    @Transactional
    override fun execute(adminId: Long) {
        adminRepository.findByIdForUpdate(adminId)?.let(adminRepository::delete)
        refreshTokenService.revoke(adminId)
    }
}
