package team.startup.expo.domain.auth.service

import team.startup.expo.domain.auth.model.ParticipantSessionLookup

interface FindParticipantSessionService {
    /** 쿠키의 불투명 ID로 [expoId]에 유효한 세션을 찾는다. 브라우저 확인과 Application의 조회가 같은 규칙을 쓴다. */
    fun execute(
        sessionId: String?,
        expoId: String,
    ): ParticipantSessionLookup
}
