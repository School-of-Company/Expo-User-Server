package team.startup.expo.domain.participation.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.startup.expo.domain.participation.entity.StandardParticipant

interface StandardParticipantRepository : JpaRepository<StandardParticipant, Long> {
    /** 전화번호는 숫자만 남겨 비교한다. 한 박람회 안에서만 훑으므로 `expo_id` 인덱스로 범위가 좁혀진다. */
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

    fun findAllByExpoIdAndIdIn(
        expoId: String,
        ids: Collection<Long>,
    ): List<StandardParticipant>
}
