package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.CreateFieldVisitParticipantsReqDto
import team.startup.expo.domain.participation.presentation.dto.request.ProjectFieldVisitReqDto
import team.startup.expo.domain.participation.presentation.dto.response.FieldVisitParticipantResDto
import java.util.UUID

interface FieldVisitService {
    fun create(
        expoId: String,
        body: CreateFieldVisitParticipantsReqDto,
    ): List<FieldVisitParticipantResDto>

    fun project(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        body: ProjectFieldVisitReqDto,
    )
}
