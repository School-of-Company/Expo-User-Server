package team.startup.expo.domain.auth.presentation.dto.response

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime

/** 브라우저에 돌려주는 세션 정보. 전화번호는 싣지 않는다. 만료 시각 형식은 토큰 응답과 같다(UTC, 오프셋 없음). */
data class ParticipantSessionResDto(
    val expoId: String,
    @get:JsonFormat(pattern = EXPIRES_AT_FORMAT)
    val expiresAt: LocalDateTime,
)

/** Application이 세션을 조회할 때 돌려주는 값. 서비스 간 호출이라 전화번호를 싣는다(본문으로만). */
data class ResolvedParticipantSessionResDto(
    val expoId: String,
    val phoneNumber: String,
    @get:JsonFormat(pattern = EXPIRES_AT_FORMAT)
    val expiresAt: LocalDateTime,
) {
    override fun toString() = "ResolvedParticipantSessionResDto(expoId=$expoId, phoneNumber=***, expiresAt=$expiresAt)"
}

/** 세션 오류. `code`는 [SESSION_REQUIRED] 또는 [SESSION_EXPIRED]이며 클라이언트가 인증 화면과 만료 안내를 가르는 데 쓴다. */
data class SessionErrorResDto(
    val status: Int,
    val code: String,
    val message: String,
) {
    companion object {
        const val SESSION_REQUIRED = "SESSION_REQUIRED"
        const val SESSION_EXPIRED = "SESSION_EXPIRED"
    }
}

private const val EXPIRES_AT_FORMAT = "yyyy-MM-dd'T'HH:mm:ss"
