package team.startup.expo.domain.user.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.startup.expo.domain.user.entity.Admin

interface AdminRepository : JpaRepository<Admin, Long> {
    fun existsByPhoneNumber(phoneNumber: String): Boolean

    fun existsByEmail(email: String): Boolean

    fun existsByNickname(nickname: String): Boolean

    fun findByNickname(nickname: String): Admin?
}
