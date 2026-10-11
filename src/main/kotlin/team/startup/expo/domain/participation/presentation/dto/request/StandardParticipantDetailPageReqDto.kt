package team.startup.expo.domain.participation.presentation.dto.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import team.startup.expo.domain.participation.entity.Occupation
import team.startup.expo.global.dto.DetailPageReqDto

/**
 * 행사 참가자 상세 목록의 커서 규칙은 [DetailPageReqDto]와 같고 후보를 거르는 조건만 더한다.
 *
 * - [q]: 이름의 대소문자 무시 부분 일치. [q]가 숫자와 하이픈·공백뿐이면 전화번호도 하이픈을 뺀 부분 일치로 찾으며,
 *   본인 번호가 없는 동행자는 대표 수신 번호로 찾는다.
 * - [occupation]: 저장된 직업 값과 정확히 같은 참가자만 남긴다.
 */
data class StandardParticipantDetailPageReqDto(
    @field:Min(0)
    val cursor: Long? = null,
    @field:Min(1)
    @field:Max(DetailPageReqDto.MAX_SIZE.toLong())
    val size: Int? = null,
    @field:Size(max = MAX_QUERY_LENGTH)
    val q: String? = null,
    val occupation: Occupation? = null,
) {
    val cursorOrZero: Long get() = cursor ?: 0L
    val sizeOrDefault: Int get() = size ?: DetailPageReqDto.MAX_SIZE
    val keyword: String get() = q?.trim().orEmpty()

    companion object {
        const val MAX_QUERY_LENGTH = 50
    }
}
