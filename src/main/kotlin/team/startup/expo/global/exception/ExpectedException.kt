package team.startup.expo.global.exception

import org.springframework.http.HttpStatus

/**
 * [retryAfterSeconds]가 있으면 응답에 `Retry-After` 헤더로 실린다. 429처럼 언제 다시 시도할 수 있는지 알려줄 때 쓴다.
 * [code]가 있으면 응답 `code`로 실린다. 같은 상태 코드의 사유를 호출자가 메시지 없이 구분해야 할 때만 쓴다(예: `FEATURE_DISABLED`).
 */
class ExpectedException(
    val status: HttpStatus,
    override val message: String,
    val retryAfterSeconds: Long? = null,
    val code: String? = null,
) : RuntimeException(message)
