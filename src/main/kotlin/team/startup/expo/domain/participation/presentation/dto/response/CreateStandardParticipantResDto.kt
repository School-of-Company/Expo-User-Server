package team.startup.expo.domain.participation.presentation.dto.response

import com.fasterxml.jackson.annotation.JsonIgnore

/** [created]는 응답 상태 코드(새로 만들면 201, 이미 있으면 200)를 정하는 데만 쓰고 본문에는 싣지 않는다. */
data class CreateStandardParticipantResDto(
    val participantId: Long,
    val phoneNumber: String,
    @get:JsonIgnore
    val created: Boolean,
)
