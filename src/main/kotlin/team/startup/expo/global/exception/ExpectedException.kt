package team.startup.expo.global.exception

import org.springframework.http.HttpStatus

/** [retryAfterSeconds]가 있으면 응답에 `Retry-After` 헤더로 실린다. 429처럼 언제 다시 시도할 수 있는지 알려줄 때 쓴다. */
class ExpectedException(
    val status: HttpStatus,
    override val message: String,
    val retryAfterSeconds: Long? = null,
) : RuntimeException(message)
