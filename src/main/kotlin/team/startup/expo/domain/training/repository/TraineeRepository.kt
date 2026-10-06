package team.startup.expo.domain.training.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.startup.expo.domain.training.entity.Trainee

interface TraineeRepository : JpaRepository<Trainee, Long> {
    fun findByExpoIdAndPhoneNumber(
        expoId: String,
        phoneNumber: String,
    ): Trainee?
}
