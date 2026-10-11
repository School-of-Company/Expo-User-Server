package team.startup.expo.domain.auth.service.impl

import org.springframework.stereotype.Service
import team.startup.expo.domain.auth.service.LogoutParticipantSessionService
import team.startup.expo.domain.auth.service.ParticipantSessionService

@Service
class LogoutParticipantSessionServiceImpl(
    private val participantSessionService: ParticipantSessionService,
) : LogoutParticipantSessionService {
    override fun execute(sessionId: String?) {
        if (sessionId.isNullOrBlank()) return
        participantSessionService.revoke(sessionId)
    }
}
