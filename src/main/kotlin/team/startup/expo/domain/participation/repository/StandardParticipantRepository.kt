package team.startup.expo.domain.participation.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.startup.expo.domain.participation.entity.StandardParticipant

interface StandardParticipantRepository : JpaRepository<StandardParticipant, Long> {
    fun findByExpoIdAndPhoneNumber(
        expoId: String,
        phoneNumber: String,
    ): StandardParticipant?

    fun findAllByExpoIdAndIdIn(
        expoId: String,
        ids: Collection<Long>,
    ): List<StandardParticipant>
}
