package team.startup.expo.domain.training.presentation.dto.request

import jakarta.validation.constraints.Size

data class GetTraineeReqDto(
    /** 이름에 포함된 글자로 거른다. 없거나 비어 있으면 전체. */
    @field:Size(max = 50)
    val name: String? = null,
)
