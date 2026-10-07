package team.startup.expo.domain.participation.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** 발행할 등록 이벤트. 참가자 저장과 같은 트랜잭션에서 만들고, 릴레이가 발행한 뒤 [publishedAt]을 채운다. */
@Entity
@Table(name = "tb_registration_outbox")
class RegistrationOutboxEvent(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @field:Column(name = "event_id", nullable = false, length = 36, unique = true)
    val eventId: String,
    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "event_type", nullable = false, length = 30)
    val eventType: RegistrationEventType,
    // Expo 소유 ID — FK 없이 보관한다
    @field:Column(name = "expo_id", nullable = false, length = 36)
    val expoId: String,
    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "participation_type", nullable = false, length = 20)
    val participationType: ParticipationType,
    @field:Column(name = "participant_id", nullable = false)
    val participantId: Long,
    // 동행자의 집계 이벤트(STANDARD_CREATED)에는 번호가 없다
    @field:Column(name = "phone_number", length = 30)
    val phoneNumber: String?,
    // 이번 문자에 담을 참가자 `[{id, code}]`. 연수자의 등록 이벤트와 집계 이벤트에는 없다
    @field:Column(name = "participants_json")
    val participantsJson: String? = null,
    @field:Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @field:Column(name = "published_at")
    var publishedAt: LocalDateTime? = null,
    @field:Column(nullable = false)
    var attempts: Int = 0,
)
