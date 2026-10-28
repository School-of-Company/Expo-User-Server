package team.startup.expo.global.client.expo.projection

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.SqlParameterValue
import org.springframework.stereotype.Repository
import java.sql.Date
import java.sql.Types
import java.time.LocalDate

/** 프로젝션 한 행. tombstone을 받은 박람회는 [deleted]가 `true`이고 기간이 없다. */
data class ExpoProjectionRow(
    val deleted: Boolean,
    val startedDay: LocalDate?,
    val finishedDay: LocalDate?,
    val deleting: Boolean,
)

@Repository
class ExpoProjectionRepository(
    private val jdbc: JdbcTemplate,
) {
    /**
     * 저장된 version보다 클 때만 박람회 행을 덮어쓰고 1을, 같거나 작으면 아무것도 하지 않고 0을 돌려준다. 같은 박람회를
     * 동시에 반영하면 늦은 쪽은 앞선 트랜잭션이 끝나길 기다렸다가 다시 비교하므로 옛 상태로 덮어쓰지 않는다.
     */
    fun upsertIfNewer(change: ExpoProjectionChange): Int =
        jdbc.update(
            """
            INSERT INTO tb_expo_projection (expo_id, version, last_event_id, deleted, started_day, finished_day, deleting, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (expo_id) DO UPDATE SET
                version = EXCLUDED.version,
                last_event_id = EXCLUDED.last_event_id,
                deleted = EXCLUDED.deleted,
                started_day = EXCLUDED.started_day,
                finished_day = EXCLUDED.finished_day,
                deleting = EXCLUDED.deleting,
                updated_at = now()
            WHERE tb_expo_projection.version < EXCLUDED.version
            """.trimIndent(),
            change.expoId,
            change.version,
            change.eventId,
            change.deleted,
            date(change.period?.startedDay),
            date(change.period?.finishedDay),
            change.deleting,
        )

    /** 박람회의 회차를 [sessionIds]로 통째로 바꾼다. 메시지에 없는 회차는 지워진 회차다. */
    fun replaceSessions(
        expoId: String,
        sessionIds: Set<Long>,
    ) {
        jdbc.update("DELETE FROM tb_expo_projection_preregister_session WHERE expo_id = ?", expoId)
        if (sessionIds.isEmpty()) return
        jdbc.batchUpdate(
            "INSERT INTO tb_expo_projection_preregister_session (expo_id, session_id) VALUES (?, ?)",
            sessionIds.map { arrayOf<Any>(expoId, it) },
        )
    }

    fun find(expoId: String): ExpoProjectionRow? =
        jdbc
            .query(
                "SELECT deleted, started_day, finished_day, deleting FROM tb_expo_projection WHERE expo_id = ?",
                { rs, _ ->
                    ExpoProjectionRow(
                        deleted = rs.getBoolean("deleted"),
                        startedDay = rs.getObject("started_day", LocalDate::class.java),
                        finishedDay = rs.getObject("finished_day", LocalDate::class.java),
                        deleting = rs.getBoolean("deleting"),
                    )
                },
                expoId,
            ).singleOrNull()

    fun sessionExists(
        expoId: String,
        sessionId: Long,
    ): Boolean =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM tb_expo_projection_preregister_session WHERE expo_id = ? AND session_id = ?)",
            Boolean::class.java,
            expoId,
            sessionId,
        ) == true

    // null을 그냥 넘기면 드라이버가 타입을 정하지 못할 수 있어 DATE로 지정한다
    private fun date(day: LocalDate?) = SqlParameterValue(Types.DATE, day?.let(Date::valueOf))
}
