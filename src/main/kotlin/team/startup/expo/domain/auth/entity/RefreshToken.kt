package team.startup.expo.domain.auth.entity

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import org.springframework.data.redis.core.TimeToLive
import org.springframework.data.redis.core.index.Indexed

/**
 * 관리자당 refresh token은 하나다. 키가 `adminId`라 새로 저장하면 이전 값이 교체된다.
 * 원문은 저장하지 않고 SHA-256 해시만 둔다.
 */
@RedisHash("refresh_token")
class RefreshToken(
    @field:Id
    val adminId: Long,
    @field:Indexed
    val tokenHash: String,
    @field:TimeToLive
    val ttlSeconds: Long,
)
