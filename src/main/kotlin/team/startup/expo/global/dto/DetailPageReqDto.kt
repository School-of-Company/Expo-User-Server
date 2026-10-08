package team.startup.expo.global.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class DetailPageReqDto(
    /** 지난 페이지의 마지막 ID. 이 ID보다 큰 행부터 읽는다. 없으면 처음부터. */
    @field:Min(0)
    val cursor: Long? = null,
    /** 한 페이지의 행 수. 없으면 500이고 최대도 500이다. */
    @field:Min(1)
    @field:Max(MAX_SIZE.toLong())
    val size: Int? = null,
) {
    val cursorOrZero: Long get() = cursor ?: 0L
    val sizeOrDefault: Int get() = size ?: MAX_SIZE

    companion object {
        const val MAX_SIZE = 500
    }
}
