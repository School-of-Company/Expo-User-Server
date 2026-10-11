package team.startup.expo.domain.auth.model

import java.time.LocalDateTime

/** 행사별로 전화 인증을 마친 참가자 세션. 만료 시각은 UTC 기준의 오프셋 없는 값이다. */
data class ParticipantSession(
    val expoId: String,
    val phoneNumber: String,
    val expiresAt: LocalDateTime,
) {
    // 기본 toString은 전화번호를 그대로 출력한다
    override fun toString() = "ParticipantSession(expoId=$expoId, phoneNumber=***, expiresAt=$expiresAt)"
}

/** 새로 만든 세션과 브라우저에 쿠키로 보낼 불투명 ID. */
data class IssuedParticipantSession(
    val sessionId: String,
    val session: ParticipantSession,
) {
    override fun toString() = "IssuedParticipantSession(sessionId=***, session=$session)"
}

/**
 * 세션 조회 결과. 쿠키가 없으면 [Required], 있는데 저장소에 없으면(만료 포함) [Expired], 다른 행사의 세션이면 존재를
 * 드러내지 않도록 [Required]로 답한다.
 */
sealed interface ParticipantSessionLookup {
    data class Valid(
        val session: ParticipantSession,
    ) : ParticipantSessionLookup

    data object Required : ParticipantSessionLookup

    data object Expired : ParticipantSessionLookup
}
