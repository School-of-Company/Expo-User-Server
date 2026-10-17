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
    /** 날짜와 상관없이 그 참가자의 입장 기록이 있는지 확인한다. */
    fun existsByStandardParticipantId(standardParticipantId: Long): Boolean

    /**
     * 그날 입장한 참가자만 참가자 id 순서로 조회한다. 같은 날 회차 없이 한 번, 회차로 한 번 입장 기록이 생길 수 있어
     * 참가자 기준으로 한 번만 센다.
     */
    @Query(
        value =
            "select new team.startup.expo.domain.participation.presentation.dto.response.ParticipantResDto(" +
                "p.id, p.name, p.phoneNumber, p.personalInformationStatus) " +
                "from StandardParticipant p where exists (select 1 from StandardParticipantParticipation pp " +
                "where pp.standardParticipant = p and pp.expoId = :expoId and pp.attendanceDate = :date) order by p.id",
        countQuery =
            "select count(p) from StandardParticipant p where exists (select 1 from StandardParticipantParticipation pp " +
                "where pp.standardParticipant = p and pp.expoId = :expoId and pp.attendanceDate = :date)",
    )
    fun findAttendees(
        @Param("expoId") expoId: String,
        @Param("date") date: LocalDate,
        pageable: Pageable,
    ): Page<ParticipantResDto>

    /** 그 사전등록 회차로 입장한 참가자만 참가자 id 순서로 조회한다. 회차가 자정을 넘겨 이틀에 기록이 있어도 한 번만 센다. */
    @Query(
        value =
            "select new team.startup.expo.domain.participation.presentation.dto.response.ParticipantResDto(" +
                "p.id, p.name, p.phoneNumber, p.personalInformationStatus) " +
                "from StandardParticipant p where exists (select 1 from StandardParticipantParticipation pp " +
                "where pp.standardParticipant = p and pp.expoId = :expoId and pp.preregisterSessionId = :sessionId) order by p.id",
        countQuery =
            "select count(p) from StandardParticipant p where exists (select 1 from StandardParticipantParticipation pp " +
                "where pp.standardParticipant = p and pp.expoId = :expoId and pp.preregisterSessionId = :sessionId)",
    )
    fun findSessionAttendees(
        @Param("expoId") expoId: String,
        @Param("sessionId") sessionId: Long,
        pageable: Pageable,
    ): Page<ParticipantResDto>
}
