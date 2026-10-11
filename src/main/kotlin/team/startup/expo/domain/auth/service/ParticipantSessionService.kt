package team.startup.expo.domain.auth.service

import team.startup.expo.domain.auth.model.IssuedParticipantSession
import team.startup.expo.domain.auth.model.ParticipantSession

interface ParticipantSessionService {
    /** 검증한 번호의 세션을 새로 만들고 쿠키에 담을 불투명 ID를 돌려준다. */
    fun issue(
        expoId: String,
        phoneNumber: String,
    ): IssuedParticipantSession

    /** 저장소에 없거나 만료되었으면 `null`이다. */
    fun find(sessionId: String): ParticipantSession?

    fun revoke(sessionId: String)

    /**
     * 검증에 성공한 인증번호를 쓴 것으로 기록한다. 처음 기록하면 `true`, 이미 쓴 번호면 `false`이다.
     * 같은 번호를 동시에 보내도 하나만 `true`를 받는다.
     */
    fun markCodeUsed(
        phoneNumber: String,
        code: String,
    ): Boolean
}
