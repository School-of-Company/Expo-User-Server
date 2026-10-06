package team.startup.expo.domain.training.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class GetTraineeNamesReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    @field:NotEmpty
    @field:Size(max = MAX_TRAINEE_IDS)
    val traineeIds: List<Long>,
) {
    companion object {
        /** 요청 하나의 상한이다. 프로그램 신청자 전체를 한 번에 보내는 호출자를 위해 넉넉하게 두고, 넘으면 호출자가 나눠 보낸다. */
        const val MAX_TRAINEE_IDS = 10_000
    }
}
