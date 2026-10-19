package team.startup.expo.domain.participation.presentation.dto.response

import com.fasterxml.jackson.annotation.JsonIgnore
import team.startup.expo.domain.participation.service.RegisteredParticipant

/**
 * [participantIds]는 이번 신청의 문자에 담는 ID 목록이고, [groupParticipantIds]는 신청 서비스가 좌석과 참가자별 원장을 맞출 때 쓰는 일행 전체 ID 목록이다. [created]는 응답 상태 코드(이번 요청이 참가자를 새로 만들었으면 201, 아니면 200)를 정하는 데만 쓰고 본문에는 싣지 않는다.
 * [createdIds]는 이번 요청이 새로 만든 참가자(대표자와 동행자)이고, [participants]는 이번 문자에 담을 참가자다. 둘 다 이벤트를
 * 만드는 데만 쓰며 본문에는 싣지 않는다. QR의 `code`가 응답에 남지 않게 하기 위해서다.
 */
data class CreateStandardParticipantResDto(
    val participantId: Long,
    val phoneNumber: String,
    @get:JsonIgnore
    val created: Boolean,
    // 이번 신청의 문자에 담은 참가자 ID(대표자 포함). `code`는 싣지 않는다
    val participantIds: List<Long> = listOf(participantId),
    // 대표자 포함 같은 일행 전체의 참가자 ID. 대표자가 먼저이고 이후는 `id` 순서이며, 기존 동행자를 포함한다. 문자 대상인
    // `participantIds`와 달리 동행자를 추가하는 재등록에서도 전체이고, 같은 요청 키의 재시도는 처음 응답과 같다. `code`는 싣지 않는다
    val groupParticipantIds: List<Long> = listOf(participantId),
    @get:JsonIgnore
    val createdIds: List<Long> = emptyList(),
    @get:JsonIgnore
    val participants: List<RegisteredParticipant> = emptyList(),
    // 새 동행자 없는 재신청이라 등록 완료 이벤트(문자)를 만들지 않는다. 이벤트를 만드는 데만 쓰며 본문에는 싣지 않는다
    @get:JsonIgnore
    val smsSkipped: Boolean = false,
)
