package team.startup.expo.domain.participation.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import team.startup.expo.domain.participation.entity.BadgePrintAttemptOutcome
import team.startup.expo.domain.participation.entity.BadgePrintJobStatus
import team.startup.expo.domain.participation.entity.ParticipationType
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class BadgePrintStation(
    val id: UUID,
    val label: String,
    val sessionId: UUID,
    val paused: Boolean,
    val lastSeenAt: LocalDateTime?,
)

data class BadgePrintJob(
    val id: Long,
    val name: String,
    val school: String?,
    val status: BadgePrintJobStatus,
    val failures: Int,
    val claimToken: UUID?,
    val claimRequestId: UUID?,
    val updatedAt: LocalDateTime,
)

data class BadgePrintAttempt(
    val claimToken: UUID,
    val jobId: Long,
    val stationId: UUID,
    val claimedAt: LocalDateTime,
    val outcome: BadgePrintAttemptOutcome?,
    val reason: String?,
    val finishedAt: LocalDateTime?,
)

data class BadgePrintReprint(
    val id: Long,
    val requestKey: UUID,
)

/** 명찰 출력 큐(`V26`). 잠그는 조회(`lock*`, [pickWaiting])는 트랜잭션 안에서 불러야 한다. 잠금 순서는 출력대 → 작업이다. */
@Repository
class BadgePrintRepository(
    private val jdbc: JdbcTemplate,
) {
    /** 자동 원본 작업을 저장한다. 같은 사람의 같은 방문일 작업이 이미 있으면 저장하지 않고 `false`를 돌려준다. */
    fun insertAutoJob(
        expoId: String,
        personKey: String,
        attendanceDate: LocalDate,
        participationType: ParticipationType,
        subjectId: Long,
        name: String,
        school: String?,
        now: LocalDateTime,
    ): Boolean =
        jdbc.update(
            "INSERT INTO tb_badge_print_job (expo_id, person_key, attendance_date, participation_type, subject_id, name, school, " +
                "status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'WAITING', ?, ?) " +
                "ON CONFLICT (expo_id, person_key, attendance_date) WHERE original_job_id IS NULL DO NOTHING",
            expoId,
            personKey,
            attendanceDate,
            participationType.name,
            subjectId,
            name,
            school,
            now,
            now,
        ) == 1

    fun insertStation(
        id: UUID,
        expoId: String,
        label: String,
        sessionId: UUID,
        now: LocalDateTime,
    ) {
        jdbc.update(
            "INSERT INTO tb_badge_print_station (id, expo_id, label, session_id, last_seen_at, created_at) VALUES (?, ?, ?, ?, ?, ?)",
            id,
            expoId,
            label,
            sessionId,
            now,
            now,
        )
    }

    fun countStations(expoId: String): Long =
        jdbc.queryForObject("SELECT count(*) FROM tb_badge_print_station WHERE expo_id = ?", Long::class.java, expoId)!!

    fun stations(expoId: String): List<BadgePrintStation> =
        jdbc.query("$STATION WHERE expo_id = ? ORDER BY created_at, id", stationRow, expoId)

    fun lockStation(
        expoId: String,
        stationId: UUID,
    ): BadgePrintStation? = jdbc.query("$STATION WHERE id = ? AND expo_id = ? FOR UPDATE", stationRow, stationId, expoId).singleOrNull()

    fun updateStation(
        stationId: UUID,
        sessionId: UUID,
        paused: Boolean,
        lastSeenAt: LocalDateTime?,
    ) {
        jdbc.update(
            "UPDATE tb_badge_print_station SET session_id = ?, paused = ?, last_seen_at = ? WHERE id = ?",
            sessionId,
            paused,
            lastSeenAt,
            stationId,
        )
    }

    /**
     * 점유 후 [deadline]까지 결과가 없는 작업을 `NEEDS_CHECK`로 닫고 시도를 `EXPIRED`로 남긴다. 다른 트랜잭션이 잡고 있는
     * 작업은 건너뛴다(그쪽이 결과를 남기거나 다음 회차가 닫는다).
     *
     * 모든 출력대의 작업을 잠그므로 한 번에 한 트랜잭션만 한다. 두 출력대의 claim이 서로의 만료 작업을 잠근 채 각자 자기 출력대의
     * 점유 작업([activeJob])을 기다리면 교착이 되기 때문이다. 이미 다른 트랜잭션이 하고 있으면 기다리지 않고 0을 돌려준다.
     */
    fun expireClaims(
        deadline: LocalDateTime,
        now: LocalDateTime,
    ): Int {
        val locked = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?, 0)", Boolean::class.java, EXPIRY_LOCK_NAMESPACE) == true
        if (!locked) return 0
        return jdbc.update(
            """
            WITH expired AS (
                UPDATE tb_badge_print_job j SET status = 'NEEDS_CHECK', updated_at = ?
                WHERE j.id IN (
                    SELECT id FROM tb_badge_print_job WHERE status = 'CLAIMED' AND claimed_at < ? FOR UPDATE SKIP LOCKED
                )
                RETURNING j.claim_token
            )
            UPDATE tb_badge_print_attempt a SET outcome = 'EXPIRED', finished_at = ?
            FROM expired e WHERE a.claim_token = e.claim_token AND a.outcome IS NULL
            """,
            now,
            deadline,
            now,
        )
    }

    /** 출력대가 점유 중인 작업. 출력대 잠금 안에서 부른다. */
    fun activeJob(stationId: UUID): BadgePrintJob? =
        jdbc.query("$JOB WHERE station_id = ? AND status = 'CLAIMED' FOR UPDATE", jobRow, stationId).singleOrNull()

    /** 점유를 결과 없이 닫는다. 종이가 나왔는지 알 수 없으므로 `NEEDS_CHECK`이다. */
    fun closeUnknown(
        job: BadgePrintJob,
        outcome: BadgePrintAttemptOutcome,
        now: LocalDateTime,
    ) {
        jdbc.update("UPDATE tb_badge_print_job SET status = 'NEEDS_CHECK', updated_at = ? WHERE id = ?", now, job.id)
        finishAttempt(requireNotNull(job.claimToken), outcome, null, now)
    }

    /** [today]의 대기 작업 중 재시도 시각이 된 가장 오래된 것을 잠근다. 다른 출력대가 잡고 있는 행은 건너뛴다. */
    fun pickWaiting(
        expoId: String,
        today: LocalDate,
        now: LocalDateTime,
    ): BadgePrintJob? =
        jdbc
            .query(
                "$JOB WHERE expo_id = ? AND attendance_date = ? AND status = 'WAITING' " +
                    "AND (next_attempt_at IS NULL OR next_attempt_at <= ?) ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED",
                jobRow,
                expoId,
                today,
                now,
            ).singleOrNull()

    fun claim(
        jobId: Long,
        stationId: UUID,
        claimToken: UUID,
        claimRequestId: UUID?,
        now: LocalDateTime,
    ) {
        jdbc.update(
            "UPDATE tb_badge_print_job SET status = 'CLAIMED', station_id = ?, claim_token = ?, claim_request_id = ?, claimed_at = ?, " +
                "next_attempt_at = NULL, updated_at = ? WHERE id = ?",
            stationId,
            claimToken,
            claimRequestId,
            now,
            now,
            jobId,
        )
        jdbc.update(
            "INSERT INTO tb_badge_print_attempt (claim_token, job_id, station_id, claimed_at) VALUES (?, ?, ?, ?)",
            claimToken,
            jobId,
            stationId,
            now,
        )
    }

    fun lockJob(
        expoId: String,
        jobId: Long,
    ): BadgePrintJob? = jdbc.query("$JOB WHERE id = ? AND expo_id = ? FOR UPDATE", jobRow, jobId, expoId).singleOrNull()

    fun jobExists(
        expoId: String,
        jobId: Long,
    ): Boolean =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM tb_badge_print_job WHERE id = ? AND expo_id = ?)",
            Boolean::class.java,
            jobId,
            expoId,
        ) == true

    fun attempt(claimToken: UUID): BadgePrintAttempt? = jdbc.query("$ATTEMPT WHERE claim_token = ?", attemptRow, claimToken).singleOrNull()

    fun attempts(jobId: Long): List<BadgePrintAttempt> = jdbc.query("$ATTEMPT WHERE job_id = ? ORDER BY claimed_at", attemptRow, jobId)

    fun finishAttempt(
        claimToken: UUID,
        outcome: BadgePrintAttemptOutcome,
        reason: String?,
        now: LocalDateTime,
    ) {
        jdbc.update(
            "UPDATE tb_badge_print_attempt SET outcome = ?, reason = ?, finished_at = ? WHERE claim_token = ? AND outcome IS NULL",
            outcome.name,
            reason,
            now,
            claimToken,
        )
    }

    fun updateJob(
        jobId: Long,
        status: BadgePrintJobStatus,
        failures: Int,
        nextAttemptAt: LocalDateTime?,
        now: LocalDateTime,
    ) {
        jdbc.update(
            "UPDATE tb_badge_print_job SET status = ?, failures = ?, next_attempt_at = ?, updated_at = ? WHERE id = ?",
            status.name,
            failures,
            nextAttemptAt,
            now,
            jobId,
        )
    }

    fun resolve(
        jobId: Long,
        status: BadgePrintJobStatus,
        adminId: Long,
        now: LocalDateTime,
    ) {
        jdbc.update(
            "UPDATE tb_badge_print_job SET status = ?, resolved_by = ?, updated_at = ? WHERE id = ?",
            status.name,
            adminId,
            now,
            jobId,
        )
    }

    fun reprintOf(jobId: Long): BadgePrintReprint? =
        jdbc
            .query(
                "SELECT id, request_key FROM tb_badge_print_job WHERE original_job_id = ?",
                { rs, _ -> BadgePrintReprint(rs.getLong(1), rs.getObject(2, UUID::class.java)) },
                jobId,
            ).singleOrNull()

    /** [originalId]를 복사한 새 대기 작업을 [today] 날짜로 만든다. 한 작업의 재출력은 하나뿐이다(`uk_badge_print_job_reprint`). */
    fun insertReprint(
        originalId: Long,
        requestKey: UUID,
        today: LocalDate,
        now: LocalDateTime,
    ) {
        jdbc.update(
            "INSERT INTO tb_badge_print_job (expo_id, person_key, attendance_date, participation_type, subject_id, name, school, " +
                "status, original_job_id, request_key, created_at, updated_at) " +
                "SELECT expo_id, person_key, ?, participation_type, subject_id, name, school, 'WAITING', id, ?, ?, ? " +
                "FROM tb_badge_print_job WHERE id = ?",
            today,
            requestKey,
            now,
            now,
            originalId,
        )
    }

    fun jobs(
        expoId: String,
        today: LocalDate,
        statuses: Collection<BadgePrintJobStatus>,
    ): List<BadgePrintJob> =
        jdbc.query(
            "$JOB WHERE expo_id = ? AND attendance_date = ? AND status = ANY (?) ORDER BY id",
            jobRow,
            expoId,
            today,
            statuses.map { it.name }.toTypedArray(),
        )

    private fun station(rs: ResultSet) =
        BadgePrintStation(
            id = rs.getObject("id", UUID::class.java),
            label = rs.getString("label"),
            sessionId = rs.getObject("session_id", UUID::class.java),
            paused = rs.getBoolean("paused"),
            lastSeenAt = rs.getTimestamp("last_seen_at")?.toLocalDateTime(),
        )

    private fun job(rs: ResultSet) =
        BadgePrintJob(
            id = rs.getLong("id"),
            name = rs.getString("name"),
            school = rs.getString("school"),
            status = BadgePrintJobStatus.valueOf(rs.getString("status")),
            failures = rs.getInt("failures"),
            claimToken = rs.getObject("claim_token", UUID::class.java),
            claimRequestId = rs.getObject("claim_request_id", UUID::class.java),
            updatedAt = rs.getTimestamp("updated_at").toLocalDateTime(),
        )

    private fun attempt(rs: ResultSet) =
        BadgePrintAttempt(
            claimToken = rs.getObject("claim_token", UUID::class.java),
            jobId = rs.getLong("job_id"),
            stationId = rs.getObject("station_id", UUID::class.java),
            claimedAt = rs.getTimestamp("claimed_at").toLocalDateTime(),
            outcome = rs.getString("outcome")?.let(BadgePrintAttemptOutcome::valueOf),
            reason = rs.getString("reason"),
            finishedAt = rs.getTimestamp("finished_at")?.toLocalDateTime(),
        )

    private val stationRow = RowMapper { rs, _ -> station(rs) }
    private val jobRow = RowMapper { rs, _ -> job(rs) }
    private val attemptRow = RowMapper { rs, _ -> attempt(rs) }

    private companion object {
        // 점유 만료 advisory lock 네임스페이스. 박람회 삭제(26), 참가자 등록(27~30)과 겹치지 않는다
        const val EXPIRY_LOCK_NAMESPACE = 31

        const val STATION = "SELECT id, label, session_id, paused, last_seen_at FROM tb_badge_print_station"
        const val JOB =
            "SELECT id, name, school, status, failures, claim_token, claim_request_id, updated_at FROM tb_badge_print_job"
        const val ATTEMPT = "SELECT claim_token, job_id, station_id, claimed_at, outcome, reason, finished_at FROM tb_badge_print_attempt"
    }
}
