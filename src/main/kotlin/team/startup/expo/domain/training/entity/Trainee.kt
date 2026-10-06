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
    @field:Column(name = "phone_number", nullable = false, length = 15)
    val phoneNumber: String,
    @field:Column(name = "training_id", nullable = false, length = 15)
    val trainingId: String,
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
)
