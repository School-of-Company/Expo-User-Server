package team.startup.expo.domain.participation.presentation.dto.response

import team.startup.expo.domain.participation.entity.Occupation

/**
 * 대표자가 관리하는 참가자 한 명. 전화번호와 개인 `code`는 싣지 않는다. 저장된 `occupation`은 신청 서비스가 회차 허용 역할과
 * 등록 자격을 판단하는 데 쓰며, 클라이언트가 보낸 답변으로 덮어쓸 수 없다.
 */
data class ManagedParticipantResDto(
    val participantId: Long,
    val name: String,
    val occupation: Occupation?,
    val representative: Boolean,
)

/** [registered]가 `false`이면 이 번호로 등록된 대표자가 없어 먼저 등록해야 한다는 뜻이다(목록은 비어 있다). */
data class GetManagedParticipantsResDto(
    val registered: Boolean,
    val participants: List<ManagedParticipantResDto>,
)
