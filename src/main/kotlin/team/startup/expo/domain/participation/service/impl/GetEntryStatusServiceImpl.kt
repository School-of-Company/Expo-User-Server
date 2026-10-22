package team.startup.expo.domain.participation.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.presentation.dto.request.GetEntryStatusReqDto
import team.startup.expo.domain.participation.presentation.dto.response.EntryStatusResDto
import team.startup.expo.domain.participation.repository.StandardParticipantParticipationRepository
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.GetEntryStatusService
import team.startup.expo.global.exception.ExpectedException

@Service
class GetEntryStatusServiceImpl(
    private val participants: StandardParticipantRepository,
    private val entries: StandardParticipantParticipationRepository,
) : GetEntryStatusService {
    @Transactional(readOnly = true)
    override fun execute(body: GetEntryStatusReqDto): EntryStatusResDto {
        if (!participants.existsByIdAndExpoId(
                body.participantId,
                body.expoId,
            )
        ) {
            throw ExpectedException(HttpStatus.NOT_FOUND, "참가자를 찾을 수 없습니다.")
        }
        return EntryStatusResDto(
            body.participantId,
            body.attendanceDate,
            entries.existsByStandardParticipantIdAndAttendanceDate(body.participantId, body.attendanceDate),
        )
    }
}
