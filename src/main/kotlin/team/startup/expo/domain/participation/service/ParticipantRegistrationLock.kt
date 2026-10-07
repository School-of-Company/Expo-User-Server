package team.startup.expo.domain.participation.service

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * 참가자 등록의 "있는지 확인하고 없으면 저장" 사이에 같은 대상의 다른 요청이 끼어들지 못하게 직렬화한다.
 * 모두 트랜잭션이 끝날 때 풀리는 advisory lock이라 호출하는 쪽이 트랜잭션 안에 있어야 한다.
 *
 * - 전화번호: 유일 제약은 표기까지 같은 `(expo_id, phone_number)`뿐이라 `010-1234-5678`과 `01012345678`을
 *   동시에 등록하면 둘 다 통과한다. 숫자만 남긴 번호로 잠가 일반 참가자와 연수자 등록이 같은 번호를 함께 직렬화한다.
 * - 연수 번호: `(expo_id, training_id)`는 현장 등록에서 같은 값을 허용하므로 유일 제약을 걸 수 없어 잠금으로 막는다.
 * - 요청 키: 같은 `requestId`의 재시도가 동시에 오면 하나씩 처리해야 앞선 요청의 기록을 뒤따르는 요청이 본다.
 *
 * 키 네임스페이스는 박람회 삭제 lock([ExpoDeletionLock], 26)과 겹치지 않는다. 삭제는 이 lock들을 잡지 않으므로
 * 서로 기다리는 순환이 생기지 않는다. 한 요청이 여럿을 잡을 때는 항상 박람회 삭제 lock([ExpoDeletionGuard]), 요청 키, 전화번호, 연수 번호 순서이다.
 */
@Component
class ParticipantRegistrationLock(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun lockRequest(requestId: String) = lock(REQUEST_NAMESPACE, requestId)

    fun lockPhone(
        expoId: String,
        digits: String,
    ) = lock(PHONE_NAMESPACE, "$expoId:$digits")

    fun lockTrainingId(
        expoId: String,
        trainingId: String,
    ) = lock(TRAINING_ID_NAMESPACE, "$expoId:$trainingId")

    private fun lock(
        namespace: Int,
        key: String,
    ) {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(?, hashtext(?))", { _ -> }, namespace, key)
    }

    private companion object {
        const val PHONE_NAMESPACE = 27
        const val TRAINING_ID_NAMESPACE = 28
        const val REQUEST_NAMESPACE = 29
    }
}
