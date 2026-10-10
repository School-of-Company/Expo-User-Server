package team.startup.expo.global.security

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import team.startup.expo.domain.user.service.BootstrapAdminService

/** 기동할 때, 설정한 닉네임으로 이미 가입해 둔 계정이 있으면 첫 관리자로 승인한다. */
@Component
class BootstrapAdminRunner(
    private val bootstrapAdminService: BootstrapAdminService,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) = bootstrapAdminService.promoteExisting()
}
