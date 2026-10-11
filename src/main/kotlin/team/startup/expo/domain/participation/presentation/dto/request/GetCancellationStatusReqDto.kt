package team.startup.expo.domain.participation.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class GetCancellationStatusReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    @field:NotBlank
    @field:Size(max = 30)
    val phoneNumber: String,
    @field:NotNull
    val participantId: Long?,
) {
    override fun toString() = "GetCancellationStatusReqDto(expoId=$expoId, phoneNumber=***, participantId=$participantId)"
}
