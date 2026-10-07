package team.startup.expo.domain.participation.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import team.startup.expo.domain.participation.entity.Occupation
import team.startup.expo.domain.training.entity.ApplicationType

data class CreateStandardParticipantReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    @field:NotBlank
    @field:Size(max = 10)
    val name: String,
    @field:NotBlank
    @field:Size(max = 15)
    val phoneNumber: String,
    val informationJson: String? = null,
    @field:NotNull
    val personalInformationStatus: Boolean,
    @field:NotNull
    val applicationType: ApplicationType,
    val occupation: Occupation? = null,
    @field:Size(max = 100)
    val school: String? = null,
    // Application의 `Idempotency-Key`. 같은 등록의 재시도에는 같은 값을 보낸다. 없으면 멱등 처리 없이 등록한다
    @field:Size(min = 1, max = 100)
    val requestId: String? = null,
)
