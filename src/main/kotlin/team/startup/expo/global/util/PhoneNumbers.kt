package team.startup.expo.global.util

import org.springframework.http.HttpStatus
import team.startup.expo.global.exception.ExpectedException

/**
 * v1은 신청 전화번호를 형식 검증 없이 입력된 그대로 저장했으므로 `010-1234-5678`처럼 하이픈이 든 값이
 * 있을 수 있다. 저장된 표기와 조회하는 쪽의 표기가 달라도 같은 번호를 찾을 수 있도록, 숫자만 남겨 비교한다.
 */
object PhoneNumbers {
    private const val MAX_DIGITS = 15

    /** 숫자만 남긴다. 숫자가 없거나 저장 가능한 길이를 넘으면 잘못된 입력이다. */
    fun digitsOnly(raw: String): String {
        val digits = raw.filter { it in '0'..'9' }
        if (digits.isEmpty() || digits.length > MAX_DIGITS) {
            throw ExpectedException(HttpStatus.BAD_REQUEST, "올바르지 않은 전화번호입니다.")
        }
        return digits
    }

    /**
     * 숫자가 같은 후보 중에서 고른다. 입력과 표기까지 같은 값이 있으면 그것을 우선하고, 후보가 하나뿐이면
     * 그것을 쓴다. 표기만 다른 후보가 여럿이라 하나로 특정할 수 없으면 엉뚱한 사람을 돌려주지 않도록 실패한다.
     */
    fun <T> select(
        candidates: List<T>,
        rawInput: String,
        phoneNumberOf: (T) -> String?,
    ): T? {
        candidates.firstOrNull { phoneNumberOf(it) == rawInput }?.let { return it }
        return when (candidates.size) {
            0 -> null
            1 -> candidates.single()
            else -> throw ExpectedException(HttpStatus.CONFLICT, "표기만 다른 같은 번호의 참가자가 여러 명이라 하나로 특정할 수 없습니다.")
        }
    }
}
