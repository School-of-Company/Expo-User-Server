package team.startup.expo.domain.participation.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class GetEntryCardsReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    @field:Size(max = MAX_IDS)
    val standardParticipantIds: List<Long> = emptyList(),
    @field:Size(max = MAX_IDS)
    val traineeIds: List<Long> = emptyList(),
) {
    companion object {
        /** 목록 하나의 상한이다. 호출자(참여 서비스)는 입장 목록 한 페이지씩 보낸다. */
        const val MAX_IDS = 1_000
    }
}
