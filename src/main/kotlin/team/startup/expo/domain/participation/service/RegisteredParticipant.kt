package team.startup.expo.domain.participation.service

/** 문자에 담을 참가자 한 명. [code]는 QR에 담기므로 로그나 조회 API에 싣지 않는다. */
data class RegisteredParticipant(
    val id: Long,
    val code: String,
)
