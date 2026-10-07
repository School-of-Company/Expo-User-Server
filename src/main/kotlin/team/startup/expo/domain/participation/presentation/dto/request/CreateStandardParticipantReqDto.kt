package team.startup.expo.domain.participation.presentation.dto.request

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import team.startup.expo.domain.participation.entity.Occupation
import team.startup.expo.domain.participation.entity.Region
import team.startup.expo.domain.training.entity.ApplicationType
import tools.jackson.databind.JsonNode

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
    // 멱등 fingerprint가 요청 본문의 직렬화로 만들어지므로, 없을 때는 싣지 않아 이 필드가 생기기 전 요청의 fingerprint를 유지한다
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val region: Region? = null,
    /** 신청 서비스가 검증에 쓴 폼 ID. 제출 당시 폼 스냅샷의 출처이며 없으면 null이다. */
    @field:Size(max = 36)
    val formId: String? = null,
    /**
     * 제출 당시 폼 문항 스냅샷(`[{"id", "title", "order", "formType", "jsonData", "otherJson", "dynamicFormType"}]`).
     * 해석 없이 보존하며, 없으면 스냅샷 없이 저장된다. 배열이 아니면 400이다.
     */
    val questions: JsonNode? = null,
    // Application의 `Idempotency-Key`. 같은 등록의 재시도에는 같은 값을 보낸다. 없으면 멱등 처리 없이 등록한다
    @field:Size(min = 1, max = 100)
    val requestId: String? = null,
)
