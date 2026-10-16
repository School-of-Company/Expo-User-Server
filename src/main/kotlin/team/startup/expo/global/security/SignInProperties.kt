package team.startup.expo.global.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 관리자 로그인 실패 횟수 제한 설정이다. 같은 닉네임으로 [lockDuration] 안에 [maxFailures]번 실패하면 그 시간이 끝날 때까지
 * 로그인을 막는다(비밀번호가 맞아도 막는다). 시도는 비밀번호 비교 전에 차감되어, 동시 요청도 [maxFailures]번까지만 비교한다. 닉네임이 없는 경우도 똑같이 세어, 제한 여부로 계정 존재를 알 수 없게 한다.
 */
@ConfigurationProperties(prefix = "signin")
data class SignInProperties(
    val maxFailures: Int = DEFAULT_MAX_FAILURES,
    val lockDuration: Duration = Duration.ofMinutes(DEFAULT_LOCK_MINUTES),
) {
    init {
        require(maxFailures > 0) { "signin.max-failures must be positive" }
        require(!lockDuration.isZero && !lockDuration.isNegative) { "signin.lock-duration must be positive" }
    }

    private companion object {
        const val DEFAULT_MAX_FAILURES = 5
        const val DEFAULT_LOCK_MINUTES = 15L
    }
}
