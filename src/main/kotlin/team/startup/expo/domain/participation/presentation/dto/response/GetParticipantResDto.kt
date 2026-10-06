package team.startup.expo.domain.participation.presentation.dto.response

/**
 * 목록 필드 이름은 `participants`다. 노션 `/participant` 명세에는 `participant`로 적혀 있지만 v1 응답과
 * `Expo-Client`(`ParticipantResponse.participants`)가 모두 `participants`를 쓴다. 노션대로 바꾸면 조회가
 * 200이어도 화면에는 빈 목록이 나온다.
 */
data class GetParticipantResDto(
    val info: ParticipantPageInfoResDto,
    val participants: List<ParticipantResDto>,
)
