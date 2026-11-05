package team.startup.expo.domain.user.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.entity.Status

interface AdminRepository : JpaRepository<Admin, Long> {
    fun existsByPhoneNumber(phoneNumber: String): Boolean

    fun existsByEmail(email: String): Boolean

    fun existsByNickname(nickname: String): Boolean

    fun findByNickname(nickname: String): Admin?

    /** 승인 대기 목록처럼 목록 응답만 필요할 때 엔티티 전체(비밀번호 해시 포함) 대신 필요한 필드만 읽는다. */
    fun findSummariesByStatus(status: Status): List<AdminSummaryView>

    fun findProfileById(id: Long): AdminProfileView?

    fun existsByStatus(status: Status): Boolean

    /**
     * 행을 잠그고 조회한다. 승인, 거절, 탈퇴가 같은 관리자에 동시에 일어날 때 서로의 결과를 보고 판단하도록
     * 직렬화한다. 반드시 트랜잭션 안에서 호출해야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Admin a where a.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: Long,
    ): Admin?

    /** [findByIdForUpdate]와 같게 행을 잠그고 닉네임으로 조회한다. 닉네임은 유일하다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Admin a where a.nickname = :nickname")
    fun findByNicknameForUpdate(
        @Param("nickname") nickname: String,
    ): Admin?
}
