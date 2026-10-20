package team.startup.expo.domain.participation.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.startup.expo.domain.participation.entity.RegistrationOutboxEvent
import java.time.LocalDateTime

interface RegistrationOutboxEventRepository : JpaRepository<RegistrationOutboxEvent, Long> {
    /**
     * 아직 발행하지 않은 이벤트를 오래된 순서로 잠가서 읽는다. 격리된 이벤트, 다른 릴레이가 점유 기한([now] 기준) 안에 보내고
     * 있는 이벤트, 같은 참가자의 앞선 이벤트가 아직 발행되지 않은 이벤트는 뺀다. 앞선 이벤트가 격리됐든, 다른 릴레이가 점유하거나
     * 잠그고 있든(`SKIP LOCKED`로 건너뛴 행도 `published_at`이 비어 있다) 뒤 이벤트는 기다리므로 릴레이가 여럿이어도
     * 참가자별 순서가 지켜진다. 그래서 한 회차에 같은 참가자의 이벤트는 한 건만 나가고 나머지는 다음 회차에 나간다.
     * 트랜잭션 안에서 불러 점유 기한을 채운 뒤 바로 커밋해야 한다.
     */
    @Query(
        value = """
            SELECT * FROM tb_registration_outbox o
            WHERE o.published_at IS NULL AND o.quarantined_at IS NULL
              AND (o.claimed_until IS NULL OR o.claimed_until < :now)
              AND NOT EXISTS (
                SELECT 1 FROM tb_registration_outbox q
                WHERE q.published_at IS NULL
                  AND q.participation_type = o.participation_type AND q.participant_id = o.participant_id AND q.id < o.id
              )
            ORDER BY o.id LIMIT :limit FOR UPDATE OF o SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun findPendingForUpdate(
        @Param("limit") limit: Int,
        @Param("now") now: LocalDateTime,
    ): List<RegistrationOutboxEvent>

    /**
     * 발행 결과를 남기고 점유를 푼다. 점유 기한이 [claimedUntil]인 행만 바꾸므로, 그사이 박람회 삭제로 지워졌거나 기한이 지나
     * 다른 릴레이가 가져간 행은 건드리지 않는다(지운 행을 되살리지 않는다).
     */
    @Modifying
    @Query(
        """
        UPDATE RegistrationOutboxEvent e
        SET e.publishedAt = :publishedAt, e.attempts = :attempts, e.lastError = :lastError, e.quarantinedAt = :quarantinedAt,
            e.claimedUntil = NULL
        WHERE e.id = :id AND e.claimedUntil = :claimedUntil
        """,
    )
    fun release(
        @Param("id") id: Long,
        @Param("claimedUntil") claimedUntil: LocalDateTime,
        @Param("publishedAt") publishedAt: LocalDateTime?,
        @Param("attempts") attempts: Int,
        @Param("lastError") lastError: String?,
        @Param("quarantinedAt") quarantinedAt: LocalDateTime?,
    ): Int
}
