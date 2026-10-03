package team.startup.expo.domain.auth.repository

import org.springframework.data.repository.CrudRepository
import team.startup.expo.domain.auth.entity.RefreshToken

interface RefreshTokenRepository : CrudRepository<RefreshToken, Long> {
    fun findByTokenHash(tokenHash: String): RefreshToken?
}
