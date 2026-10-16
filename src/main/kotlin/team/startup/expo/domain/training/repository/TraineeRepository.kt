package team.startup.expo.domain.training.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.startup.expo.domain.training.entity.Trainee

interface TraineeRepository : JpaRepository<Trainee, Long> {
    /** 사전등록 참가자에 이어진 연수자. 참가자 한 명에 연수자 한 명이다. */
    fun findByStandardParticipantId(standardParticipantId: Long): Trainee?

    /** 참가자 여럿에 이어진 연수자를 한 번에 읽는다. 연결이 없는 참가자는 결과에 나오지 않는다. */
    fun findByStandardParticipantIdIn(standardParticipantIds: Collection<Long>): List<TraineeParticipantLinkView>

    /** 연수원 아이디가 비어 있을 때만 채운다. 이미 값이 있으면 아무것도 바꾸지 않고 0을 돌려준다. */
    @Modifying
    @Query("UPDATE Trainee t SET t.trainingId = :trainingId WHERE t.id = :id AND t.trainingId IS NULL")
    fun fillTrainingIdIfEmpty(
        @Param("id") id: Long,
        @Param("trainingId") trainingId: String,
    ): Int

    fun findByIdAndExpoId(
        id: Long,
        expoId: String,
    ): Trainee?

    /** 영속성 컨텍스트가 아닌 DB의 현재 연결. 조건부 갱신이 0건일 때 누가 먼저 바꿨는지 다시 읽는 데 쓴다. */
    @Query("SELECT t.standardParticipantId FROM Trainee t WHERE t.id = :id")
    fun findStandardParticipantIdById(
        @Param("id") id: Long,
    ): Long?

    /** 이어지지 않은 연수자만 잇는다. 이미 연결이 있으면(먼저 이은 요청이 있으면) 아무것도 바꾸지 않고 0을 돌려준다. */
    @Modifying
    @Query("UPDATE Trainee t SET t.standardParticipantId = :participantId WHERE t.id = :id AND t.standardParticipantId IS NULL")
    fun linkParticipantIfUnlinked(
        @Param("id") id: Long,
        @Param("participantId") participantId: Long,
    ): Int

    /** 지금 [participantId]에 이어져 있을 때만 연결을 푼다. 다른 연결로 바뀌었으면 0을 돌려준다. */
    @Modifying
    @Query("UPDATE Trainee t SET t.standardParticipantId = NULL WHERE t.id = :id AND t.standardParticipantId = :participantId")
    fun unlinkParticipantIfLinkedTo(
        @Param("id") id: Long,
        @Param("participantId") participantId: Long,
    ): Int

    /** 저장된 표기와 정확히 같은 번호. `(expo_id, phone_number)` 유일 인덱스를 탄다. */
    fun findByExpoIdAndPhoneNumber(
        expoId: String,
        phoneNumber: String,
    ): Trainee?

    /** 같은 박람회에 같은 연수 번호가 여러 명 있을 수 있으므로(유일 제약은 전화번호뿐이다) 목록으로 읽어 중복을 구분한다. */
    fun findAllByExpoIdAndTrainingId(
        expoId: String,
        trainingId: String,
    ): List<TraineeNameView>

    /** `id` 오름차순으로 [cursor]보다 큰 행을 읽는다. 다음 페이지가 있는지 알 수 있도록 호출자가 한 행 더 요청한다. */
    fun findByExpoIdAndIdGreaterThanOrderById(
        expoId: String,
        cursor: Long,
        pageable: Pageable,
    ): List<Trainee>

    fun existsByExpoIdAndTrainingId(
        expoId: String,
        trainingId: String,
    ): Boolean

    fun findNamesByExpoIdAndIdIn(
        expoId: String,
        ids: Collection<Long>,
    ): List<TraineeNameView>

    fun findByExpoIdOrderById(expoId: String): List<TraineeInfoView>

    /** `Containing`은 이름에 든 `%`와 `_`를 와일드카드가 아닌 글자로 취급한다. */
    fun findByExpoIdAndNameContainingOrderById(
        expoId: String,
        name: String,
    ): List<TraineeInfoView>

    /** 숫자만 남겨 비교한다. `idx_trainee_expo_phone_digits` 표현식 인덱스와 같은 식이어야 인덱스를 탄다. */
    @Query(
        value =
            "SELECT * FROM tb_trainee " +
                "WHERE expo_id = :expoId AND regexp_replace(phone_number, '[^0-9]', '', 'g') = :digits ORDER BY id",
        nativeQuery = true,
    )
    fun findAllByExpoIdAndDigits(
        @Param("expoId") expoId: String,
        @Param("digits") digits: String,
    ): List<Trainee>
}
