package team.startup.expo.domain.user.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.startup.expo.domain.user.entity.Admin

interface AdminRepository : JpaRepository<Admin, Long>
