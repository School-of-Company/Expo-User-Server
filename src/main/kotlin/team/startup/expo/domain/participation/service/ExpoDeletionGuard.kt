package team.startup.expo.domain.participation.service

import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import team.startup.expo.global.exception.ExpectedException

/**
 * 박람회 삭제와 직렬화하는 읽기 쪽 가드다. 쓰기는 `V4` 트리거가 같은 lock을 잡아 막지만, 이미 있는 행을 읽어 돌려주기만
 * 하는 경로는 트리거를 타지 않는다. 삭제 트랜잭션이 행을 지우고 아직 커밋하지 않은 동안 그 행을 읽으면 곧 사라질 ID를
 * 돌려주게 되므로, 같은 박람회의 공유 lock(삭제는 배타 lock)을 잡아 삭제가 끝나길 기다린 뒤 삭제 기록을 확인한다.
 * 트랜잭션 안에서 불러야 하고, lock은 트랜잭션이 끝날 때 풀린다. 한 요청이 여러 lock을 잡을 때는 이 lock이 먼저다.
 */
@Component
class ExpoDeletionGuard(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun requireNotDeleted(expoId: String) {
        if (isDeleted(expoId)) throw ExpectedException(HttpStatus.CONFLICT, "삭제 중이거나 삭제된 박람회입니다.")
    }

    /**
     * 같은 박람회의 삭제가 진행 중이면 끝나길 기다린 뒤 삭제 기록이 있는지 돌려준다. 삭제 중이거나 삭제된 박람회면 `true`이다.
     * 설문 답변 컨슈머처럼 예외 대신 건너뛰어야 하는 호출자가 쓴다.
     */
    fun isDeleted(expoId: String): Boolean {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock_shared(?, hashtext(?))", { _ -> }, ExpoDeletionLock.NAMESPACE, expoId)
        return jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM tb_expo_deletion WHERE expo_id = ?)",
            Boolean::class.java,
            expoId,
        ) ==
            true
    }
}
