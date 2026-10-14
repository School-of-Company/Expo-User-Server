package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.response.ParticipantStatsResDto

interface GetParticipantStatsService {
    fun execute(expoId: String): ParticipantStatsResDto
}
