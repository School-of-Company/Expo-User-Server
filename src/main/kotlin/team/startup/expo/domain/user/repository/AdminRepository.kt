package team.startup.expo.domain.user.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.entity.Status

interface AdminRepository : JpaRepository<Admin, Long> {
    fun existsByPhoneNumber(phoneNumber: String): Boolean

    fun existsByEmail(email: String): Boolean

    fun existsByNickname(nickname: String): Boolean

    fun findByNickname(nickname: String): Admin?

    fun findByStatus(status: Status): List<Admin>
}
