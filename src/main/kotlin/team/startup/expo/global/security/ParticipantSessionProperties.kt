package team.startup.expo.global.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 참가자(관리자가 아닌 신청자) 전화 인증 세션 설정이다. 세션은 행사별로 검증한 전화번호를 Redis에 두고, 브라우저에는
 * 추측할 수 없는 불투명 ID만 `HttpOnly` 쿠키로 보낸다. 관리자 JWT와는 별개다.
 *
 * - [ttl]은 신청 세션의 유효 시간이다. 인증번호(OTP) 자체의 유효 시간은 Notification이 관리하므로 따로다.
 * - [usedCodeTtl]은 검증에 성공한 인증번호를 다시 쓰지 못하게 기억해 두는 시간이다. Notification의 인증번호 유효 시간
 *   이상이어야 같은 번호로 세션을 또 만들지 못한다.
 * - 쿠키는 기본이 `Secure`이고 `SameSite=Lax`다. 로컬 HTTP 개발에서만 [secure]를 끈다.
 */
@ConfigurationProperties(prefix = "participant-session")
data class ParticipantSessionProperties(
    val ttl: Duration = Duration.ofMinutes(DEFAULT_TTL_MINUTES),
    val usedCodeTtl: Duration = Duration.ofMinutes(DEFAULT_USED_CODE_TTL_MINUTES),
    val cookieName: String = "PARTICIPANT_SESSION",
    val secure: Boolean = true,
    val sameSite: String = "Lax",
    val domain: String = "",
) {
    init {
        require(!ttl.isZero && !ttl.isNegative) { "participant-session.ttl must be positive" }
        require(!usedCodeTtl.isZero && !usedCodeTtl.isNegative) { "participant-session.used-code-ttl must be positive" }
        require(cookieName.isNotBlank()) { "participant-session.cookie-name must not be blank" }
        require(sameSite in SAME_SITE_VALUES) { "participant-session.same-site must be one of $SAME_SITE_VALUES" }
        // SameSite=None은 Secure가 아니면 브라우저가 쿠키를 버린다
        require(sameSite != "None" || secure) { "participant-session.secure must be true when same-site is None" }
    }

    private companion object {
        const val DEFAULT_TTL_MINUTES = 30L
        const val DEFAULT_USED_CODE_TTL_MINUTES = 10L
        val SAME_SITE_VALUES = setOf("Strict", "Lax", "None")
    }
}
