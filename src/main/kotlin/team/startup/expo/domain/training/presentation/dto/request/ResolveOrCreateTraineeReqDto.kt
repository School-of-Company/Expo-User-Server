package team.startup.expo.domain.training.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import tools.jackson.databind.JsonNode

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
    /** 신청 서비스가 검증에 쓴 폼 ID. 제출 당시 폼 스냅샷의 출처이며 없으면 null이다. */
    @field:Size(max = 36)
    val formId: String? = null,
    /**
     * 제출 당시 폼 문항 스냅샷(`[{"id", "title", "order", "formType", "jsonData", "otherJson", "dynamicFormType"}]`).
     * 해석 없이 보존하며, 없으면 스냅샷 없이 저장된다. 배열이 아니면 400이다.
     */
    val questions: JsonNode? = null,
)
