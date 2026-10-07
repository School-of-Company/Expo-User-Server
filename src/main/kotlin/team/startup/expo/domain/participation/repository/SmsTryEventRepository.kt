package team.startup.expo.domain.participation.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.startup.expo.domain.participation.entity.SmsTryEvent

interface SmsTryEventRepository : JpaRepository<SmsTryEvent, String> {
    /**
     * 처음 보는 `eventId`면 기록하고 1을, 이미 있으면 0을 돌려준다. 같은 `eventId`를 동시에 기록하면 늦은 쪽은
     * 앞선 트랜잭션이 끝나길 기다렸다가 0을 받으므로, 횟수는 한 번만 올라간다.
     */
    @Modifying
    @Query(
        value =
            "INSERT INTO tb_sms_try_event (event_id, participant_id) VALUES (:eventId, :participantId) " +
                "ON CONFLICT (event_id) DO NOTHING",
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("eventId") eventId: String,
        @Param("participantId") participantId: Long,
    ): Int
}
