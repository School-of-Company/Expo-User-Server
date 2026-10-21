package team.startup.expo.domain.participation.presentation.dto.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Positive
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

data class GetParticipantReqDto(
    /** 0부터 시작한다. 없으면 0. */
    @field:Min(0)
    val page: Int? = null,
    /** 한 페이지의 인원. 없으면 80이고, 너무 큰 요청을 막기 위해 최대 1,000이다. */
    @field:Min(1)
    @field:Max(MAX_SIZE.toLong())
    val size: Int? = null,
    /** 이 날짜에 입장한 참가자를 조회한다. 없으면 오늘. */
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val date: LocalDate? = null,
    /** 이 사전등록 회차로 입장한 참가자를 조회한다. 있으면 `date`보다 우선하며 `date`는 무시한다. */
    @field:Positive
    val sessionId: Long? = null,
    /** true면 `date`에 회차 없이 입장한 참가자를 조회한다(현장등록 입장 등). `sessionId`와 함께 줄 수 없다. */
    val withoutSession: Boolean? = null,
) {
    companion object {
        const val DEFAULT_SIZE = 80
        const val MAX_SIZE = 1_000
    }
}
