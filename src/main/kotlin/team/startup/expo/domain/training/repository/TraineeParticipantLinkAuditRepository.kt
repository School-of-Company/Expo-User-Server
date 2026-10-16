package team.startup.expo.domain.training.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.startup.expo.domain.training.entity.TraineeParticipantLinkAudit

interface TraineeParticipantLinkAuditRepository : JpaRepository<TraineeParticipantLinkAudit, Long> {
    fun findFirstByTraineeIdOrderByIdDesc(traineeId: Long): TraineeParticipantLinkAudit?
}
