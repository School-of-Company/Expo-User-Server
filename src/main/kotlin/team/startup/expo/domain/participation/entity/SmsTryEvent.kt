package team.startup.expo.domain.participation.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** 발송 횟수를 이미 올린 문자 이벤트. 같은 `eventId`는 횟수를 한 번만 올리기 위한 기록이다. */
@Entity
@Table(name = "tb_sms_try_event")
class SmsTryEvent(
    @field:Id
    @field:Column(name = "event_id", length = 36)
    val eventId: String,
    // 참가자 삭제 때 DB의 ON DELETE CASCADE로 함께 지워진다
    @field:Column(name = "participant_id", nullable = false)
    val participantId: Long,
    @field:Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
