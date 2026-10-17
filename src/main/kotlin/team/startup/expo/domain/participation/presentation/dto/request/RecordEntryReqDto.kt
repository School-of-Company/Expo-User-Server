package team.startup.expo.domain.participation.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import team.startup.expo.domain.participation.entity.ParticipationType

data class RecordEntryReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    val participationType: ParticipationType,
    // 일반 참가자는 `participantId`와 `code`로도 찾을 수 있다(동행자는 번호가 없다). 연수자는 번호가 필요하다
    @field:Size(max = 30)
    val phoneNumber: String? = null,
    val participantId: Long? = null,
    @field:Size(max = 22)
    val code: String? = null,
    // 입장 스캔이 신청한 사전등록 회차의 입장 시간임을 확인했을 때만 보낸다. 그 밖의 입장은 보내지 않아 회차 없이 기록된다.
    // 일반 참가자만 받는다
    @field:Positive
    val preregisterSessionId: Long? = null,
)
