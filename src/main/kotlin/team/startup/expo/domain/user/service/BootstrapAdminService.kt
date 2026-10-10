package team.startup.expo.domain.user.service

interface BootstrapAdminService {
    /** 첫 관리자를 승인했으면 `true`, 아무것도 하지 않았으면 `false`를 돌려준다. */
    fun execute(): Boolean
}
