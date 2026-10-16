package team.startup.expo.domain.training.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** 레거시 연수자를 운영자가 참가자에 잇거나 그 연결을 해제한 기록. 상태가 실제로 바뀐 경우에만 남는다. */
@Entity
@Table(name = "tb_trainee_participant_link_audit")
class TraineeParticipantLinkAudit(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @field:Column(name = "trainee_id", nullable = false)
    val traineeId: Long,
    @field:Column(name = "standard_participant_id", nullable = false)
    val standardParticipantId: Long,
    @field:Column(name = "expo_id", nullable = false, length = 36)
    val expoId: String,
    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 10)
    val action: TraineeParticipantLinkAction,
    @field:Column(name = "actor_admin_id", nullable = false)
    val actorAdminId: Long,
    @field:Column(nullable = false, length = 500)
    val reason: String,
    @field:Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
