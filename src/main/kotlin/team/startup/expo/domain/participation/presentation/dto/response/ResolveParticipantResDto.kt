package team.startup.expo.domain.participation.presentation.dto.response

import team.startup.expo.domain.participation.entity.ParticipationType

data class ResolveParticipantResDto(
    val participantId: Long,
    val participationType: ParticipationType,
)
