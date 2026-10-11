package team.startup.expo.domain.auth.service

import team.startup.expo.domain.auth.model.IssuedParticipantSession
import team.startup.expo.domain.auth.model.ParticipantSession

interface ParticipantSessionService {
    /**
     * 검증에 성공한 인증번호를 쓴 것으로 기록하면서 그 번호의 세션을 새로 만들고, 쿠키에 담을 불투명 ID를 돌려준다. 이미 쓴
     * 인증번호면 세션을 만들지 않고 `null`이다. 사용 기록과 세션 저장은 하나의 원자적 Redis 작업이라, 둘 중 하나만 남는 일이
     * 없고 같은 코드를 동시에 보내도 하나만 세션을 받는다.
     */
    fun issueIfCodeUnused(
        expoId: String,
        phoneNumber: String,
        code: String,
    ): IssuedParticipantSession?

    /** 저장소에 없거나 만료되었으면 `null`이다. */
    fun find(sessionId: String): ParticipantSession?

    fun revoke(sessionId: String)
}
