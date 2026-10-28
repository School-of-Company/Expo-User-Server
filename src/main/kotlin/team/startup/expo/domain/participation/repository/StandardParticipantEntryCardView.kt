package team.startup.expo.domain.participation.repository

import team.startup.expo.domain.participation.entity.Occupation

/** 입장 목록의 한 행(이름, 전화번호, 개인정보 동의, 직업, 학교)만 읽는 투영. `information_json`을 읽지 않는다. */
interface StandardParticipantEntryCardView {
    val id: Long
    val name: String
    val phoneNumber: String?
    val personalInformationStatus: Boolean
    val occupation: Occupation?
    val school: String?
}
