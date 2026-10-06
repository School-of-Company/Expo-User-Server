package team.startup.expo.domain.participation.repository

/** 참가자와 설문 답변을 이어 주는 투영. 참가자 엔티티를 읽지 않고 답변 JSON만 가져온다. */
interface SurveyAnswerView {
    val participantId: Long
    val answerJson: String
}
