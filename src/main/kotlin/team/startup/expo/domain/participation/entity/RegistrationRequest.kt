package team.startup.expo.domain.participation.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** 처리를 마친 등록 요청. 같은 `requestId`가 다시 오면 저장 없이 이 기록의 결과를 그대로 돌려준다. */
@Entity
@Table(name = "tb_registration_request")
class RegistrationRequest(
    @field:Id
    @field:Column(name = "request_id", length = 100)
    val requestId: String,
    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "participation_type", nullable = false, length = 20)
    val participationType: ParticipationType,
    // Expo 소유 ID — FK 없이 보관한다
    @field:Column(name = "expo_id", nullable = false, length = 36)
    val expoId: String,
    @field:Column(nullable = false, length = 64)
    val fingerprint: String,
    @field:Column(name = "participant_id", nullable = false)
    val participantId: Long,
    @field:Column(name = "phone_number", nullable = false, length = 30)
    val phoneNumber: String,
    @field:Column(nullable = false)
    val created: Boolean,
    // 응답에 돌려준 참가자 ID 목록(JSON 배열). 이 컬럼이 생기기 전의 행은 null이다
    @field:Column(name = "participant_ids")
    val participantIds: String? = null,
    @field:Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
