package team.startup.expo.domain.participation.presentation.dto.response

/**
 * 박람회 관리자 화면의 참가자 집계. [verifiedPhoneCount]는 이 박람회에서 전화 인증을 마친 **고유 번호** 수이고(같은 번호의 재인증은
 * 한 번만 센다, 전화 인증 기록을 시작한 이후의 인증만 센다), [registeredParticipantCount]는 등록한 고유 참가자 수(`participantId`
 * 기준, 동행자 포함)다.
 */
data class ParticipantStatsResDto(
    val expoId: String,
    val verifiedPhoneCount: Long,
    val registeredParticipantCount: Long,
)
