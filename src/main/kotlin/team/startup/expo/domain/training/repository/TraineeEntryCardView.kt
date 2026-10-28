package team.startup.expo.domain.training.repository

/** 입장 목록의 한 행(이름, 전화번호, 개인정보 동의, 학교)만 읽는 투영. `information_json`을 읽지 않는다. */
interface TraineeEntryCardView {
    val id: Long
    val name: String
    val phoneNumber: String?
    val personalInformationStatus: Boolean
    val school: String?
}
