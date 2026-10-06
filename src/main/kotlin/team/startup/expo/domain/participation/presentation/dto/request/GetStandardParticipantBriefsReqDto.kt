package team.startup.expo.domain.participation.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class GetStandardParticipantBriefsReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    @field:NotEmpty
    @field:Size(max = GetStandardParticipantNamesReqDto.MAX_PARTICIPANT_IDS)
    val participantIds: List<Long>,
)
