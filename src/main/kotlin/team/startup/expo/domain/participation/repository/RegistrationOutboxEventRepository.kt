package team.startup.expo.domain.participation.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.startup.expo.domain.participation.entity.RegistrationOutboxEvent

interface RegistrationOutboxEventRepository : JpaRepository<RegistrationOutboxEvent, Long> {
    /**
     * 아직 발행하지 않은 이벤트를 오래된 순서로 잠가서 읽는다. 격리된 이벤트와, 같은 참가자의 앞선 이벤트가 격리돼 있는
     * 이벤트는 빼서 참가자별 순서를 지킨다. `SKIP LOCKED`라 인스턴스가 여럿이어도 같은 이벤트를 동시에 발행하지 않는다.
     * 트랜잭션 안에서 불러야 한다.
     */
    @Query(
        value = """
            SELECT * FROM tb_registration_outbox o
            WHERE o.published_at IS NULL AND o.quarantined_at IS NULL
              AND NOT EXISTS (
                SELECT 1 FROM tb_registration_outbox q
                WHERE q.published_at IS NULL AND q.quarantined_at IS NOT NULL
                  AND q.participation_type = o.participation_type AND q.participant_id = o.participant_id AND q.id < o.id
              )
            ORDER BY o.id LIMIT :limit FOR UPDATE OF o SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun findPendingForUpdate(
        @Param("limit") limit: Int,
    ): List<RegistrationOutboxEvent>
}
