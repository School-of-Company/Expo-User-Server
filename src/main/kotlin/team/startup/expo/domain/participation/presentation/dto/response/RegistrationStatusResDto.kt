package team.startup.expo.domain.participation.presentation.dto.response

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

enum class RegistrationStatus { SAVED, NOT_SAVED }

data class RegistrationStatusResDto(
    @field:Schema(description = "SAVED: 이 요청의 등록이 저장됨, NOT_SAVED: 저장되지 않았고 이후에도 저장되지 않음(취소 기록을 남김)")
    val status: RegistrationStatus,
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    @field:Schema(description = "SAVED일 때 대표자 참가자 ID. NOT_SAVED면 없다", nullable = true)
    val participantId: Long? = null,
)
