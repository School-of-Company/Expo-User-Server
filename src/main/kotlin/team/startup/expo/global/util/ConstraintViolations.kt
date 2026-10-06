package team.startup.expo.global.util

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import team.startup.expo.global.exception.ExpectedException
import java.sql.SQLException

/**
 * 참가자 저장이 DB 제약에 걸렸을 때 500 대신 호출자가 구분할 수 있는 상태로 바꾼다.
 * 알 수 없는 제약 위반은 그대로 돌려주어 서버 오류로 남긴다.
 */
object ConstraintViolations {
    private const val UNIQUE_VIOLATION = "23505"

    // 삭제 중이거나 삭제된 박람회에 참가자를 만들려 할 때 트리거가 일으킨다(V4)
    private const val CHECK_VIOLATION = "23514"

    fun translate(exception: DataIntegrityViolationException): Throwable {
        val sqlState =
            generateSequence<Throwable>(exception) { it.cause }
                .filterIsInstance<SQLException>()
                .firstOrNull()
                ?.sqlState
        return when (sqlState) {
            UNIQUE_VIOLATION -> ExpectedException(HttpStatus.CONFLICT, "이미 신청한 참가자입니다.")
            CHECK_VIOLATION -> ExpectedException(HttpStatus.CONFLICT, "삭제 중이거나 삭제된 박람회입니다.")
            else -> exception
        }
    }
}
