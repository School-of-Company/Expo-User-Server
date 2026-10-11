package team.startup.expo.domain.participation.presentation.dto.response

/**
 * QR에 담는 `participantId`와 `code`. `code`는 QR 위조를 막는 값이라 이 응답 본문 외에는 로그나 오류 메시지에 남기지
 * 않는다. 기본 `toString`이 값을 찍지 않도록 가린다.
 */
data class ParticipantQrResDto(
    val participantId: Long,
    val code: String,
) {
    override fun toString() = "ParticipantQrResDto(participantId=$participantId, code=***)"
}
