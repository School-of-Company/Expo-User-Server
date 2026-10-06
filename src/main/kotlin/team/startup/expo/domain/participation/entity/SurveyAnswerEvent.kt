package team.startup.expo.domain.participation.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** 처리를 마친 설문 답변 이벤트. 같은 `eventId`가 다시 오면 이 기록의 결과를 그대로 돌려준다. */
@Entity
@Table(name = "tb_survey_answer_event")
class SurveyAnswerEvent(
    @field:Id
    @field:Column(name = "event_id", length = 36)
    val eventId: String,
    // Form 서비스 소유 ID — FK 없이 보관한다
    @field:Column(name = "survey_id", nullable = false, length = 36)
    val surveyId: String,
    @field:Column(name = "expo_id", nullable = false, length = 36)
    val expoId: String,
    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 20)
    val status: SurveyAnswerStatus,
    @field:Column(length = 255)
    val reason: String? = null,
    @field:Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
