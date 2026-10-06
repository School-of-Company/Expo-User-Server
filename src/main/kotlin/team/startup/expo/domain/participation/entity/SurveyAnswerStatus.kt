package team.startup.expo.domain.participation.entity

/** 설문 답변 이벤트의 처리 결과. Form 서비스의 결과 이벤트 `status`와 같은 값을 쓴다. */
enum class SurveyAnswerStatus {
    STORED,
    REJECTED,
}
