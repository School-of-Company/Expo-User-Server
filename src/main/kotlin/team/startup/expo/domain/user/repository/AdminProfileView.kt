package team.startup.expo.domain.user.repository

/** 내 정보 응답에 필요한 필드만 읽는다. 비밀번호 해시와 전화번호는 조회하지 않는다. */
interface AdminProfileView {
    val name: String
    val nickname: String
    val email: String
}
