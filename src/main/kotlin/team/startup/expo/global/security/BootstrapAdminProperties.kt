package team.startup.expo.global.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 빈 DB에서 첫 관리자를 만들기 위한 설정이다. 기동할 때 승인된 관리자가 한 명도 없고 [nickname]으로 가입한 승인 대기
 * 계정이 있으면 그 계정을 관리자로 승인한다(가입할 때는 승인하지 않는다). 비워 두면 아무것도 하지 않는다.
 * 비밀번호는 받지 않고 가입은 평소처럼 한다.
 */
@ConfigurationProperties(prefix = "bootstrap-admin")
data class BootstrapAdminProperties(
    val nickname: String = "",
)
