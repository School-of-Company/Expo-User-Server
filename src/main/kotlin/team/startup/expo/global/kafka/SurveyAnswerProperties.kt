package team.startup.expo.global.kafka

import org.springframework.boot.context.properties.ConfigurationProperties

/** Form 서비스와 주고받는 설문 답변 이벤트의 토픽 이름. */
@ConfigurationProperties(prefix = "survey-answer")
data class SurveyAnswerProperties(
    val submitTopic: String,
    val resultTopic: String,
    val deadLetterTopic: String,
)
