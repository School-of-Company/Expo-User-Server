package team.startup.expo.domain.participation.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** 저장되지 않았다고 확정한 등록 요청. 같은 `requestId`의 이후 등록은 저장하지 않고 409로 거절한다. */
@Entity
@Table(name = "tb_registration_cancellation")
class RegistrationCancellation(
    @field:Id
    @field:Column(name = "request_id", length = 100)
    val requestId: String,
    // Expo 소유 ID — FK 없이 보관한다
    @field:Column(name = "expo_id", nullable = false, length = 36)
    val expoId: String,
    @field:Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
