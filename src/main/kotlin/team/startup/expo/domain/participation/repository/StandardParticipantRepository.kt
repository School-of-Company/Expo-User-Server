package team.startup.expo.domain.participation.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.startup.expo.domain.participation.entity.StandardParticipant

interface StandardParticipantRepository : JpaRepository<StandardParticipant, Long> {
    /** 저장된 표기와 정확히 같은 번호. `(expo_id, phone_number)` 유일 인덱스를 탄다. */
    fun findByExpoIdAndPhoneNumber(
        expoId: String,
        phoneNumber: String,
    ): StandardParticipant?

    /** 숫자만 남겨 비교한다. `idx_standard_participant_expo_phone_digits` 표현식 인덱스와 같은 식이어야 인덱스를 탄다. */
    @Query(
        value =
            "SELECT * FROM tb_standard_participant " +
                "WHERE expo_id = :expoId AND regexp_replace(phone_number, '[^0-9]', '', 'g') = :digits ORDER BY id",
        nativeQuery = true,
    )
    fun findAllByExpoIdAndDigits(
        @Param("expoId") expoId: String,
        @Param("digits") digits: String,
    ): List<StandardParticipant>

    /** `id` 오름차순으로 [cursor]보다 큰 행을 읽는다. 다음 페이지가 있는지 알 수 있도록 호출자가 한 행 더 요청한다. */
    fun findByExpoIdAndIdGreaterThanOrderById(
        expoId: String,
        cursor: Long,
        pageable: Pageable,
    ): List<StandardParticipant>

    fun findBriefsByExpoIdAndIdIn(
        expoId: String,
        ids: Collection<Long>,
    ): List<StandardParticipantBriefView>

    /** 참가자 엔티티(`information_json` 포함)를 읽지 않고 그 박람회의 참가자인지만 확인한다. */
    fun existsByIdAndExpoId(
        id: Long,
        expoId: String,
    ): Boolean

    fun findNamesByExpoIdAndIdIn(
        expoId: String,
        ids: Collection<Long>,
    ): List<StandardParticipantNameView>

    /** 읽고 쓰는 사이에 다른 요청이 끼어도 횟수가 덮어써지지 않도록 DB에서 한 번에 올린다. */
    @Modifying
    @Query("UPDATE StandardParticipant p SET p.smsTryTime = p.smsTryTime + 1 WHERE p.id = :id")
    fun increaseSmsTryTime(
        @Param("id") id: Long,
    ): Int
}
