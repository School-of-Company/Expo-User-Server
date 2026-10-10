package team.startup.expo.domain.user.service.impl

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.user.entity.Status
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.domain.user.service.BootstrapAdminService
import team.startup.expo.global.security.BootstrapAdminProperties

/**
 * 새 서버(빈 DB)에는 가입을 승인해 줄 관리자가 없어 아무도 로그인할 수 없다. 설정한 닉네임([BootstrapAdminProperties])의
 * 승인 대기 계정을 [Admin.accept]로 승인해(상태와 권한을 함께 바꾼다) 첫 관리자로 만든다.
 *
 * 안전을 위해 다음을 모두 지킨다.
 * - 승인된 관리자가 한 명도 없을 때만 한다. 관리자가 생긴 뒤에는 설정이 남아 있어도 아무 일도 하지 않으므로 여러 번 실행해도 같다.
 * - 가입할 때가 아니라 기동할 때만 한다. 가입 직후 바로 관리자가 되면 설정을 켜 둔 동안 닉네임을 먼저 가입한 사람이
 *   곧바로 관리자가 된다. 가입한 계정이 의도한 사람의 것인지 확인한 뒤 재기동해야 승인된다.
 * - 승인 대기 상태의 계정만 승인한다. 행을 잠근 뒤 다시 확인해 여러 인스턴스가 동시에 기동해도 한 번만 승인한다.
 * - 로그에는 닉네임만 남기고 그 밖의 개인정보는 남기지 않는다.
 */
@Service
class BootstrapAdminServiceImpl(
    private val properties: BootstrapAdminProperties,
    private val adminRepository: AdminRepository,
) : BootstrapAdminService {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun execute(): Boolean {
        val nickname = properties.nickname.trim()
        if (nickname.isEmpty()) return false
        if (adminRepository.existsByStatus(Status.ACCEPTED)) {
            log.info("승인된 관리자가 이미 있어 첫 관리자 승인을 건너뜁니다. BOOTSTRAP_ADMIN_NICKNAME을 비워 두세요.")
            return false
        }

        val candidate = adminRepository.findByNickname(nickname)
        if (candidate == null) {
            log.warn("첫 관리자로 승인할 계정이 없습니다. 이 닉네임으로 가입한 뒤 다시 기동하세요: nickname={}", nickname)
            return false
        }
        // 행을 잠근 뒤 상태를 다시 확인한다
        val admin = adminRepository.findByIdForUpdate(requireNotNull(candidate.id)) ?: return false
        if (admin.status != Status.PENDING || adminRepository.existsByStatus(Status.ACCEPTED)) return false

        admin.accept()
        adminRepository.save(admin)
        log.info("승인된 관리자가 없어 첫 관리자로 승인했습니다: nickname={}", nickname)
        return true
    }
}
