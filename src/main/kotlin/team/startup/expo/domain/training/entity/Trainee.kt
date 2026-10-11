package team.startup.expo.domain.training.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

@Entity
@Table(
    name = "tb_trainee",
    uniqueConstraints = [UniqueConstraint(columnNames = ["expo_id", "phone_number"])],
    indexes = [Index(columnList = "expo_id, name")],
)
class Trainee(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    // Expo 서비스 소유 — FK 없이 ID만 보관한다
    @field:Column(name = "expo_id", nullable = false, length = 36)
    val expoId: String,
    @field:Column(nullable = false, length = 10)
    val name: String,
    // 참가자에서 이어 만든 연수자는 번호가 없고 문자는 notificationPhoneNumber로 간다
    @field:Column(name = "phone_number", length = 15)
    val phoneNumber: String?,
    // 연수원 아이디. 참가자에서 이어 만든 연수자는 아직 모를 수 있다
    @field:Column(name = "training_id", length = 15)
    val trainingId: String?,
    @field:JdbcTypeCode(SqlTypes.JSON)
    @field:Column(name = "information_json", columnDefinition = "jsonb")
    val informationJson: String? = null,
    @field:Column(name = "personal_information_status", nullable = false)
    val personalInformationStatus: Boolean,
    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "application_type", nullable = false, length = 10)
    val applicationType: ApplicationType,
    @field:Column(name = "application_date", nullable = false)
    val applicationDate: LocalDateTime,
    // 명찰 출력에 쓴다. 신청 서비스가 폼에서 꺼내 넘기며 없으면 null이다
    @field:Column(length = 100)
    val school: String? = null,
    // 신청 당시 폼 스냅샷. 신청 서비스가 검증에 쓴 폼 ID와 문항이며 없으면 null이다
    @field:Column(name = "information_form_id", length = 36)
    val informationFormId: String? = null,
    @field:JdbcTypeCode(SqlTypes.JSON)
    @field:Column(name = "information_questions", columnDefinition = "jsonb")
    val informationQuestions: String? = null,
    // 문자를 받을 번호. 이어 만든 연수자가 대표자나 본인 번호를 쓰도록 따로 둔다. 없으면 phoneNumber로 보낸다
    @field:Column(name = "notification_phone_number", length = 15)
    val notificationPhoneNumber: String? = null,
    // 이어진 사전등록 참가자. 같은 박람회의 참가자와 1:1이며 이어지지 않은 연수자는 null이다
    @field:Column(name = "standard_participant_id")
    val standardParticipantId: Long? = null,
)
