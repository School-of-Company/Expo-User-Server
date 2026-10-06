package team.startup.expo.domain.participation.presentation.dto.response

/** 노션 명세의 응답 형태다. 목록 필드 이름은 `participants`가 아니라 `participant`다. */
data class GetParticipantResDto(
    val info: ParticipantPageInfoResDto,
    val participant: List<ParticipantResDto>,
)
