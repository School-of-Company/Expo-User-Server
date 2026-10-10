package team.startup.expo.domain.user.service.impl

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.entity.Status
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.domain.user.service.BootstrapAdminService
import team.startup.expo.global.security.BootstrapAdminProperties

/**
 * 새 서버(빈 DB)에는 승인을 해 줄 관리자가 없어서 아무도 로그인할 수 없다. 설정한 닉네임([BootstrapAdminProperties])의
 * 계정에 한해, 승인된 관리자가 한 명도 없을 때만 승인한다. 닉네임은 유일하므로 그 닉네임의 계정은 하나뿐이고,
 * 관리자가 생긴 뒤에는 이 설정이 남아 있어도 아무 일도 하지 않는다.
 */
@Service
class BootstrapAdminServiceImpl(
    private val properties: BootstrapAdminProperties,
    private val adminRepository: AdminRepository,
) : BootstrapAdminService {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun promoteIfFirst(admin: Admin): Boolean {
        if (properties.nickname.isBlank() || admin.nickname != properties.nickname) return false
        if (adminRepository.existsByStatus(Status.ACCEPTED)) return false

        admin.accept()
        adminRepository.save(admin)
        log.info("승인된 관리자가 없어 첫 관리자로 승인했습니다: nickname={}", admin.nickname)
        return true
    }

    @Transactional
    override fun promoteExisting() {
        if (properties.nickname.isBlank()) return
        val admin = adminRepository.findByNickname(properties.nickname) ?: return
        promoteIfFirst(admin)
    }
}
