package team.startup.expo.domain.participation.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.startup.expo.domain.participation.entity.RegistrationCancellation

interface RegistrationCancellationRepository : JpaRepository<RegistrationCancellation, String>
