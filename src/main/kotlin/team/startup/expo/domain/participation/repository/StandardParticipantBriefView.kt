package team.startup.expo.domain.participation.repository

/** 이름, 전화번호, 개인정보 동의 여부만 필요할 때 `information_json`을 읽지 않도록 하는 투영. */
interface StandardParticipantBriefView {
    val id: Long
    val name: String
    val phoneNumber: String
    val personalInformationStatus: Boolean
}
