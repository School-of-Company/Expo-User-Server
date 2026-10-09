package team.startup.expo.domain.participation.service

/**
 * 문자에 담을 참가자 한 명. [code]는 QR에 담기므로 로그나 조회 API에 싣지 않는다.
 *
 * [name]은 문자의 링크 라벨에 쓰는 이름이다(대표자와 동행자 모두). 선택 필드라 이 필드가 생기기 전에 저장된 아웃박스 행은
 * 이름 없이(`null`) 읽히고, 받는 쪽은 이름이 없으면 라벨을 대체한다.
 */
data class RegisteredParticipant(
    val id: Long,
    val code: String,
    val name: String? = null,
)
