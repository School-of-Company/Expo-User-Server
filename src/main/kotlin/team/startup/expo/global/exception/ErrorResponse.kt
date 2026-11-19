package team.startup.expo.global.exception

import com.fasterxml.jackson.annotation.JsonInclude

/** `errors`는 요청 검증 실패일 때만 담기며 필드 이름과 그 필드의 실패 사유를 짝지은 값이다. `code`는 사유 코드가 있을 때만 담긴다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val status: Int,
    val message: String,
    val errors: Map<String, String>? = null,
    val code: String? = null,
)
