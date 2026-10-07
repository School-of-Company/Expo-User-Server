package team.startup.expo.domain.participation.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.startup.expo.domain.participation.entity.RegistrationOutboxEvent

interface RegistrationOutboxEventRepository : JpaRepository<RegistrationOutboxEvent, Long> {
    /**
     * 아직 발행하지 않은 이벤트를 오래된 순서로 잠가서 읽는다. `SKIP LOCKED`라 인스턴스가 여럿이어도 같은
     * 이벤트를 동시에 발행하지 않는다. 트랜잭션 안에서 불러야 한다.
     */
    @Query(
        value = "SELECT * FROM tb_registration_outbox WHERE published_at IS NULL ORDER BY id LIMIT :limit FOR UPDATE SKIP LOCKED",
        nativeQuery = true,
    )
    fun findPendingForUpdate(
        @Param("limit") limit: Int,
    ): List<RegistrationOutboxEvent>
}
