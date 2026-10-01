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
import java.time.LocalDateTime

@Entity
@Table(
    name = "tb_standard_participant",
    uniqueConstraints = [UniqueConstraint(columnNames = ["expo_id", "phone_number"])],
    indexes = [Index(columnList = "phone_number")],
)
class StandardParticipant(
    // Expo 서비스 소유 — FK 없이 ID만 보관한다
    @Column(name = "expo_id", nullable = false, length = 36)
    val expoId: String,
    @Column(nullable = false, length = 10)
    val name: String,
    @Column(nullable = false, length = 15)
    val phoneNumber: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val informationJson: String? = null,
    @Column(nullable = false)
    val personalInformationStatus: Boolean,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val applicationType: ApplicationType,
    @Column(nullable = false)
    var smsTryTime: Int = 0,
    @Column(nullable = false)
    val applicationDate: LocalDateTime,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
) {
    fun plusSmsTryTime() {
        smsTryTime++
    }
}
