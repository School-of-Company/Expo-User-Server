package team.startup.expo.domain.participation.repository

/** 참가자 등록 당시의 답변 원천만 읽는 투영. 이름, 전화번호, 개인 `code`는 읽지 않는다. */
interface ParticipantRegistrationView {
    val id: Long
    val expoId: String
    val informationJson: String?

    /** 등록 당시 문항 스냅샷. 스냅샷 없이 저장된 행(동행자 포함)은 `null`이다. */
    val informationQuestions: String?
}
