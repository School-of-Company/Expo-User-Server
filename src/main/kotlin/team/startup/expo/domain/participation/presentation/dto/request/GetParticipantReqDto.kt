package team.startup.expo.domain.participation.presentation.dto.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
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
) {
    companion object {
        const val DEFAULT_SIZE = 80
        const val MAX_SIZE = 1_000
    }
}
