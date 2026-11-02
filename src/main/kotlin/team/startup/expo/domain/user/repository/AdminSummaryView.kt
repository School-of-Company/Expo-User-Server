package team.startup.expo.domain.user.repository

/** 승인 대기 목록에 필요한 필드만 읽는다. 비밀번호 해시는 조회하지 않는다. */
interface AdminSummaryView {
    val id: Long
    val name: String
    val nickname: String
    val email: String
    val phoneNumber: String
}
