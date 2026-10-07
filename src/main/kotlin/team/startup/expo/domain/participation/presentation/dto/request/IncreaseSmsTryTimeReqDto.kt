package team.startup.expo.domain.participation.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import team.startup.expo.domain.participation.entity.ParticipationType

data class IncreaseSmsTryTimeReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    val participationType: ParticipationType,
    @field:NotBlank
    @field:Size(max = 30)
    val phoneNumber: String,
    // 문자 이벤트의 `eventId`. 같은 값으로 여러 번 호출해도 횟수는 한 번만 올라간다.
    // Notification이 보내기 전까지는 없어도 받는다(없으면 호출마다 올린다). 배포가 끝나면 필수로 바꾼다
    @field:Size(min = 1, max = 36)
    val eventId: String? = null,
)
