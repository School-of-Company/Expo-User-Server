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
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "tb_standard_participant_participation",
    uniqueConstraints = [UniqueConstraint(columnNames = ["expo_id", "standard_participant_id", "attendance_date"])],
)
class StandardParticipantParticipation(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @field:Column(name = "entry_time", nullable = false)
    val entryTime: LocalDateTime,
    @field:Column(name = "attendance_date", nullable = false)
    val attendanceDate: LocalDate,
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "standard_participant_id")
    @field:OnDelete(action = OnDeleteAction.CASCADE)
    val standardParticipant: StandardParticipant,
    @field:Column(name = "expo_id", nullable = false, length = 36)
    val expoId: String,
)
