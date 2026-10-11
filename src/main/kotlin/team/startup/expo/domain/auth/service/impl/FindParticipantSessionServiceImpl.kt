package team.startup.expo.domain.auth.service.impl

import org.springframework.stereotype.Service
import team.startup.expo.domain.auth.model.ParticipantSessionLookup
import team.startup.expo.domain.auth.service.FindParticipantSessionService
import team.startup.expo.domain.auth.service.ParticipantSessionService

/**
 * 쿠키가 없으면 세션 필요, 저장소에 없으면(만료 포함) 세션 만료, 다른 행사의 세션이면 존재를 드러내지 않도록 세션 필요로
 * 답한다. 세션이 만료되면 사용자는 같은 화면으로 돌아와 다시 인증한다.
 */
@Service
class FindParticipantSessionServiceImpl(
    private val participantSessionService: ParticipantSessionService,
) : FindParticipantSessionService {
    override fun execute(
        sessionId: String?,
        expoId: String,
    ): ParticipantSessionLookup {
        if (sessionId.isNullOrBlank()) return ParticipantSessionLookup.Required
        val session = participantSessionService.find(sessionId) ?: return ParticipantSessionLookup.Expired
        if (session.expoId != expoId) return ParticipantSessionLookup.Required
        return ParticipantSessionLookup.Valid(session)
    }
}
