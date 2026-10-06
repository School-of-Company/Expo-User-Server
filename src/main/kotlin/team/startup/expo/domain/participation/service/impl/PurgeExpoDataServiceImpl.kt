package team.startup.expo.domain.participation.service.impl

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.service.ExpoDeletionLock
import team.startup.expo.domain.participation.service.PurgeExpoDataService

/**
 * `Expo` 서비스의 박람회 삭제가 호출한다. `Expo`는 이 호출이 성공한 뒤에만 자기 행을 지우므로, 실패하면 예외로
 * 끝나 재시도할 수 있어야 하고, 이미 지웠거나 데이터가 없어도 성공해야 한다.
 *
 * 모두 한 트랜잭션이라 중간에 실패하면 삭제 기록과 데이터가 함께 되돌아간다. 입장 기록과 설문 답변은 참가자에
 * `ON DELETE CASCADE`로 걸려 있어 참가자를 지우면 같이 지워진다.
 */
@Service
class PurgeExpoDataServiceImpl(
    private val jdbcTemplate: JdbcTemplate,
) : PurgeExpoDataService {
    @Transactional
    override fun execute(expoId: String) {
        // 같은 박람회에 대한 쓰기(공유 lock)가 끝나길 기다리고, 이후의 쓰기는 이 트랜잭션이 끝날 때까지 막는다
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(?, hashtext(?))", { _ -> }, ExpoDeletionLock.NAMESPACE, expoId)
        jdbcTemplate.update(
            "INSERT INTO tb_expo_deletion (expo_id, started_at) VALUES (?, (now() AT TIME ZONE 'UTC')) ON CONFLICT (expo_id) DO NOTHING",
            expoId,
        )
        jdbcTemplate.update("DELETE FROM tb_trainee WHERE expo_id = ?", expoId)
        jdbcTemplate.update("DELETE FROM tb_standard_participant WHERE expo_id = ?", expoId)
        jdbcTemplate.update("UPDATE tb_expo_deletion SET completed_at = (now() AT TIME ZONE 'UTC') WHERE expo_id = ?", expoId)
    }
}
