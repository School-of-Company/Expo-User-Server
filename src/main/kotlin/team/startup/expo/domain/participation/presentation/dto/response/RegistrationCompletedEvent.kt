package team.startup.expo.domain.participation.presentation.dto.response

import team.startup.expo.domain.participation.entity.ParticipationType

/** 등록이 끝나 QR 문자를 보낼 대상이 생겼다는 이벤트. Notification이 `eventId`를 멱등키로 소비한다. */
data class RegistrationCompletedEvent(
    val eventId: String,
    val expoId: String,
    val participationType: ParticipationType,
    val id: Long,
    val phoneNumber: String,
)
