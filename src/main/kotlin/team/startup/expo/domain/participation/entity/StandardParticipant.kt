package team.startup.expo.domain.participation.entity

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
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.global.util.ParticipantCode
import java.time.LocalDateTime

@Entity
@Table(
    name = "tb_standard_participant",
    uniqueConstraints = [UniqueConstraint(columnNames = ["expo_id", "phone_number"])],
    indexes = [Index(columnList = "phone_number")],
)
class StandardParticipant(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    // Expo 서비스 소유 — FK 없이 ID만 보관한다
    @field:Column(name = "expo_id", nullable = false, length = 36)
    val expoId: String,
    @field:Column(nullable = false, length = 10)
    val name: String,
    // 동행자는 번호가 없고 문자는 대표자 번호로 간다
    @field:Column(name = "phone_number", length = 15)
    val phoneNumber: String?,
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
    // 명찰 대상 판정과 출력에 쓴다. 신청 서비스가 폼에서 꺼내 넘기며 없으면 null이다
    @field:Enumerated(EnumType.STRING)
    @field:Column(length = 30)
    val occupation: Occupation? = null,
    @field:Column(length = 100)
    val school: String? = null,
    // 신청 당시 폼 스냅샷. 신청 서비스가 검증에 쓴 폼 ID와 문항이며 없으면 null이다
    @field:Column(name = "information_form_id", length = 36)
    val informationFormId: String? = null,
    @field:JdbcTypeCode(SqlTypes.JSON)
    @field:Column(name = "information_questions", columnDefinition = "jsonb")
    val informationQuestions: String? = null,
    // 동행자가 속한 대표자. 대표자는 null이다
    @field:Column(name = "representative_id")
    val representativeId: Long? = null,
    @field:Enumerated(EnumType.STRING)
    @field:Column(length = 20)
    val region: Region? = null,
    // QR에 담는 값이다. 만들 때 정하고 바꾸지 않으며 조회 API나 로그에 싣지 않는다
    @field:Column(nullable = false, length = 22, unique = true, updatable = false)
    val code: String = ParticipantCode.generate(),
    smsTryTime: Int = 0,
) {
    @field:Column(name = "sms_try_time", nullable = false)
    var smsTryTime: Int = smsTryTime
        protected set

    fun plusSmsTryTime() {
        smsTryTime++
    }
}
