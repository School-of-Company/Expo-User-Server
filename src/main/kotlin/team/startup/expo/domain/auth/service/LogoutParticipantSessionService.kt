package team.startup.expo.domain.auth.service

interface LogoutParticipantSessionService {
    /** 세션을 폐기한다. 쿠키가 없거나 이미 없는 세션이어도 성공한다. */
    fun execute(sessionId: String?)
}
