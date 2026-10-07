package team.startup.expo.domain.participation.presentation.dto.request

import team.startup.expo.domain.participation.entity.ParticipationType

/**
 * Notification 서비스가 QR 문자 발송에 성공한 뒤 `notification.qr-sms.sent` 토픽으로 발행하는 발송 완료 이벤트.
 * `eventId`는 Notification이 소비한 등록 완료 이벤트의 `eventId`를 그대로 쓴 값이라 재발행해도 같다.
 * 전화번호는 싣지 않는다. 참가자는 `id`로 찾는다.
 */
data class QrSmsSentEvent(
    val eventId: String,
    val expoId: String,
    val participationType: ParticipationType,
    val id: Long,
)
