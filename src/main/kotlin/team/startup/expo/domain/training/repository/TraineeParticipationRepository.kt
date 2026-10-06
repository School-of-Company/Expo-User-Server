package team.startup.expo.domain.training.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.startup.expo.domain.training.entity.TraineeParticipation

interface TraineeParticipationRepository : JpaRepository<TraineeParticipation, Long>
