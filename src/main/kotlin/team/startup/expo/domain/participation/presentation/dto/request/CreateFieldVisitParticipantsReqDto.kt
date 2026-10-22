package team.startup.expo.domain.participation.presentation.dto.request

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import team.startup.expo.domain.participation.entity.Occupation
import team.startup.expo.domain.participation.entity.Region
import tools.jackson.databind.JsonNode

data class CreateFieldVisitParticipantsReqDto(
    @field:NotBlank
    @field:Size(max = 15)
    val phoneNumber: String,
    @field:NotBlank
    @field:Size(max = 100)
    val requestId: String,
    @field:Valid
    @field:Size(min = 1, max = 5)
    val participants: List<NewFieldVisitParticipant>,
    val personalInformationStatus: Boolean,
    @field:Size(max = 36)
    val formId: String? = null,
    val questions: JsonNode? = null,
) {
    override fun toString() = "CreateFieldVisitParticipantsReqDto(requestId=$requestId)"
}

data class NewFieldVisitParticipant(
    @field:NotBlank
    @field:Size(max = 100)
    val clientReference: String,
    @field:NotBlank
    @field:Size(max = 10)
    val name: String,
    val occupation: Occupation? = null,
    @field:Size(max = 100)
    val school: String? = null,
    val region: Region? = null,
    val informationJson: String = "{}",
)
