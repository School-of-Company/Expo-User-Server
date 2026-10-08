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

    fun findByStatus(status: Status): List<Admin>

    /**
     * 행을 잠그고 조회한다. 승인, 거절, 탈퇴가 같은 관리자에 동시에 일어날 때 서로의 결과를 보고 판단하도록
     * 직렬화한다. 반드시 트랜잭션 안에서 호출해야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Admin a where a.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: Long,
    ): Admin?
}
