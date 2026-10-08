package team.startup.expo.domain.participation.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.hibernate.type.SqlTypes

@Entity
@Table(
    name = "tb_standard_participant_survey_answer",
    uniqueConstraints = [UniqueConstraint(columnNames = ["survey_id", "standard_participant_id"])],
)
class StandardParticipantSurveyAnswer(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    // Form 서비스 소유 — FK 없이 ID만 보관한다
    @field:Column(name = "survey_id", nullable = false, length = 36)
    val surveyId: String,
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "standard_participant_id")
    @field:OnDelete(action = OnDeleteAction.CASCADE)
    val standardParticipant: StandardParticipant,
    // Form 서비스가 검증을 마친 답변을 JSON 그대로 넘기고, 이 서비스는 해석 없이 저장한다
    @field:JdbcTypeCode(SqlTypes.JSON)
    @field:Column(name = "answer_json", nullable = false, columnDefinition = "jsonb")
    val answerJson: String,
    @field:Column(name = "personal_information_status", nullable = false)
    val personalInformationStatus: Boolean,
    // 제출 당시 설문 문항 스냅샷. v1 이벤트처럼 스냅샷 없이 저장된 행은 null이다
    @field:JdbcTypeCode(SqlTypes.JSON)
    @field:Column(name = "answer_questions", columnDefinition = "jsonb")
    val answerQuestions: String? = null,
)
