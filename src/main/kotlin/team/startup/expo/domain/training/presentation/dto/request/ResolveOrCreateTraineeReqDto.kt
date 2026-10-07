package team.startup.expo.domain.training.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class ResolveOrCreateTraineeReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    @field:NotBlank
    @field:Size(max = 15)
    val trainingId: String,
    @field:NotBlank
    @field:Size(max = 10)
    val name: String,
    @field:NotBlank
    @field:Size(max = 15)
    val phoneNumber: String,
    val informationJson: String? = null,
    @field:NotNull
    val personalInformationStatus: Boolean,
    /** 명찰 출력에 쓴다. 새로 만들 때만 저장하고 없으면 null이다. */
    @field:Size(max = 100)
    val school: String? = null,
)
