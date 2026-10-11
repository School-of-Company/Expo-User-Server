package team.startup.expo.domain.participation.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class SelectManagedParticipantsReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    @field:NotBlank
    @field:Size(max = 30)
    val phoneNumber: String,
    // 대표자 포함 한 신청의 최대 인원과 같다
    @field:NotEmpty
    @field:Size(max = MAX_PARTICIPANT_IDS)
    val participantIds: List<Long>,
) {
    override fun toString() = "SelectManagedParticipantsReqDto(expoId=$expoId, phoneNumber=***, participantIds=$participantIds)"

    companion object {
        const val MAX_PARTICIPANT_IDS = 5
    }
}
