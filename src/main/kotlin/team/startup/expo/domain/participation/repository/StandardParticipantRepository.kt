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

    /** 대표자에 딸린 동행자를 등록 순서대로 읽는다. */
    fun findAllByRepresentativeIdOrderById(representativeId: Long): List<StandardParticipant>

    /** 연결된 연수자의 등록 답변 원천을 한 번에 읽는다. 참가자 엔티티 전체를 읽지 않고 답변 열만 가져온다. */
    @Query(
        "select p.id as id, p.expoId as expoId, p.informationJson as informationJson, " +
            "p.informationQuestions as informationQuestions from StandardParticipant p where p.id in :ids",
    )
    fun findRegistrationsByIdIn(
        @Param("ids") ids: Collection<Long>,
    ): List<ParticipantRegistrationView>

    /** 대표자 본인과 그 대표자의 동행자를 `id` 순서로 읽는다. 첫 파라미터는 대표자의 `id`, 둘째는 같은 값이다. */
    fun findAllByIdOrRepresentativeIdOrderById(
        id: Long,
        representativeId: Long,
    ): List<ManagedParticipantView>

    fun findByIdAndExpoId(
        id: Long,
        expoId: String,
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

    /**
     * 커서 목록을 조건으로 거른다. 빈 문자열은 그 조건을 쓰지 않는다는 뜻이다. [namePattern]은 소문자 `LIKE` 패턴(이스케이프 문자 `!`),
     * [digits]는 하이픈을 뺀 숫자이며 비어 있으면 번호는 찾지 않는다. 본인 번호가 없으면 대표자의 번호로 찾는다.
     */
    @Query(
        value =
            "SELECT p.* FROM tb_standard_participant p LEFT JOIN tb_standard_participant r ON r.id = p.representative_id " +
                "WHERE p.expo_id = :expoId AND p.id > :cursor " +
                "AND (:occupation = '' OR p.occupation = :occupation) " +
                "AND (:namePattern = '' OR lower(p.name) LIKE :namePattern ESCAPE '!' " +
                "OR (:digits <> '' AND regexp_replace(coalesce(p.phone_number, r.phone_number, ''), '[^0-9]', '', 'g') " +
                "LIKE '%' || :digits || '%')) " +
                "ORDER BY p.id LIMIT :limit",
        nativeQuery = true,
    )
    fun searchByExpoId(
        @Param("expoId") expoId: String,
        @Param("cursor") cursor: Long,
        @Param("occupation") occupation: String,
        @Param("namePattern") namePattern: String,
        @Param("digits") digits: String,
        @Param("limit") limit: Int,
    ): List<StandardParticipant>

    /** 그 박람회의 일반 참가자 수. 동행자도 각각 한 명으로 센다. */
    fun countByExpoId(expoId: String): Long

    fun findBriefsByExpoIdAndIdIn(
        expoId: String,
        ids: Collection<Long>,
    ): List<StandardParticipantBriefView>

    fun findEntryCardsByExpoIdAndIdIn(
        expoId: String,
        ids: Collection<Long>,
    ): List<StandardParticipantEntryCardView>

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
