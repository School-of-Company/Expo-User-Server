package team.startup.expo.domain.participation.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.startup.expo.domain.participation.entity.StandardParticipantParticipation
import team.startup.expo.domain.participation.presentation.dto.response.ParticipantResDto
import java.time.LocalDate

interface StandardParticipantParticipationRepository : JpaRepository<StandardParticipantParticipation, Long> {
    /** 그날 입장한 참가자만 참가자 id 순서로 조회한다. 참가자 한 명은 하루에 한 번만 입장 기록이 생긴다. */
    @Query(
        value =
            "select new team.startup.expo.domain.participation.presentation.dto.response.ParticipantResDto(" +
                "p.id, p.name, p.phoneNumber, p.personalInformationStatus) " +
                "from StandardParticipantParticipation pp join pp.standardParticipant p " +
                "where pp.expoId = :expoId and pp.attendanceDate = :date order by p.id",
        countQuery = "select count(pp) from StandardParticipantParticipation pp where pp.expoId = :expoId and pp.attendanceDate = :date",
    )
    fun findAttendees(
        @Param("expoId") expoId: String,
        @Param("date") date: LocalDate,
        pageable: Pageable,
    ): Page<ParticipantResDto>
}
