package team.startup.expo.domain.user.service

import team.startup.expo.domain.user.entity.Admin

interface BootstrapAdminService {
    /** 승인된 관리자가 없고 설정한 닉네임의 계정이면 관리자로 승인한다. 승인했으면 true. */
    fun promoteIfFirst(admin: Admin): Boolean

    /** 설정한 닉네임으로 이미 가입한 계정이 있으면 [promoteIfFirst]를 적용한다. 기동할 때 부른다. */
    fun promoteExisting()
}
