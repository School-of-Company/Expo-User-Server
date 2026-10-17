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
    // DB 제약은 회차가 NULL인 입장끼리도 중복으로 본다(`NULLS NOT DISTINCT`, V19)
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_standard_participant_participation",
            columnNames = ["expo_id", "standard_participant_id", "attendance_date", "preregister_session_id"],
        ),
    ],
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
    /** 신청한 사전등록 회차의 입장 시간에 들어온 입장만 회차가 있다. 현장 입장·신청 없음·시간 밖·V19 이전 기록은 `null`이다. */
    @field:Column(name = "preregister_session_id")
    val preregisterSessionId: Long? = null,
)
