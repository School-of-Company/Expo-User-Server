package team.startup.expo.domain.participation.presentation.dto.response

import com.fasterxml.jackson.annotation.JsonIgnore
import team.startup.expo.domain.participation.service.RegisteredParticipant

/**
 * [created]는 응답 상태 코드(이번 요청이 참가자를 새로 만들었으면 201, 아니면 200)를 정하는 데만 쓰고 본문에는 싣지 않는다.
 * [createdIds]는 이번 요청이 새로 만든 참가자(대표자와 동행자)이고, [participants]는 이번 문자에 담을 참가자다. 둘 다 이벤트를
 * 만드는 데만 쓰며 본문에는 싣지 않는다. QR의 `code`가 응답에 남지 않게 하기 위해서다.
 */
data class CreateStandardParticipantResDto(
    val participantId: Long,
    val phoneNumber: String,
    @get:JsonIgnore
    val created: Boolean,
    @get:JsonIgnore
    val createdIds: List<Long> = emptyList(),
    @get:JsonIgnore
    val participants: List<RegisteredParticipant> = emptyList(),
)
