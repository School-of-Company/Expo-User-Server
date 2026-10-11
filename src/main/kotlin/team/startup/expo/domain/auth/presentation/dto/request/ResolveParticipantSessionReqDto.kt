package team.startup.expo.domain.auth.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** `sessionId`는 브라우저가 보낸 쿠키 값이다. 쿠키가 없었으면 비워서 보낸다(세션 필요로 답한다). */
data class ResolveParticipantSessionReqDto(
    val sessionId: String? = null,
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
) {
    override fun toString() = "ResolveParticipantSessionReqDto(sessionId=***, expoId=$expoId)"
}
