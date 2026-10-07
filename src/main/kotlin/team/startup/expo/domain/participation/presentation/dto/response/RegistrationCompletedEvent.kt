package team.startup.expo.domain.participation.presentation.dto.response

import com.fasterxml.jackson.annotation.JsonInclude
import team.startup.expo.domain.participation.entity.ParticipationType
import team.startup.expo.domain.participation.service.RegisteredParticipant

/**
 * 등록이 끝나 QR 문자를 보낼 대상이 생겼다는 이벤트. Notification이 `eventId`를 멱등키로 소비한다.
 *
 * `id`와 `phoneNumber`는 문자를 받는 대표자(연수자는 본인)다. 일반 참가자는 `representativeId`(`id`와 같다)와, 이번 문자에
 * 담을 참가자 `participants`(처음 신청이면 대표자와 동행자 전원, 새 동행자만 추가했으면 그 동행자들, 재발송이면 기존 전원)를 더해
 * 보낸다. 연수자는 두 필드가 없다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RegistrationCompletedEvent(
    val eventId: String,
    val expoId: String,
    val participationType: ParticipationType,
    val id: Long,
    val phoneNumber: String?,
    val representativeId: Long? = null,
    val participants: List<RegisteredParticipant>? = null,
)
