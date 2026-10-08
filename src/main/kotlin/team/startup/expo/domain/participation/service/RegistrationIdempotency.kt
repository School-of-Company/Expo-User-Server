package team.startup.expo.domain.participation.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import team.startup.expo.domain.participation.entity.ParticipationType
import team.startup.expo.domain.participation.entity.RegistrationRequest
import team.startup.expo.domain.participation.repository.RegistrationRequestRepository
import team.startup.expo.global.exception.ExpectedException
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest

/**
 * 같은 `requestId`로 재시도한 등록은 저장하지 않고 처음의 결과를 돌려준다. 호출하는 쪽이 트랜잭션 안에 있어야 하며,
 * 결과 기록은 등록과 같은 트랜잭션에서 확정된다.
 *
 * 같은 키가 다른 내용으로 오면 409다. 내용은 `requestId`를 뺀 요청 본문과 응답자 구분의 해시로 비교한다.
 * 같은 키의 요청은 [ParticipantRegistrationLock]으로 직렬화하고, 삭제 중이거나 삭제된 박람회는 [ExpoDeletionGuard]로 거부한다. 첫 요청이 실패한(예외로 끝난) 키는 기록이
 * 남지 않으므로 다시 처리한다.
 */
@Component
class RegistrationIdempotency(
    private val registrationRequestRepository: RegistrationRequestRepository,
    private val expoDeletionGuard: ExpoDeletionGuard,
    private val registrationLock: ParticipantRegistrationLock,
    private val jsonMapper: JsonMapper,
) {
    /** [requestId]가 없으면 멱등 처리 없이 [action]만 실행한다. [body]는 `requestId`를 뺀 요청 본문이다. */
    fun execute(
        requestId: String?,
        participationType: ParticipationType,
        expoId: String,
        body: Any,
        action: () -> Registration,
    ): Registration {
        if (requestId == null) return action()

        // 기록을 읽어 돌려주기만 하는 재시도는 삭제 트리거를 타지 않는다. 삭제와 겹치면 곧 사라질 참가자 ID를 돌려주게 되므로
        // 요청 기록을 읽기 전에 삭제를 기다리고 삭제 기록을 확인한다. 삭제 lock이 요청 키 lock보다 먼저다
        expoDeletionGuard.requireNotDeleted(expoId)
        registrationLock.lockRequest(requestId)
        val fingerprint = fingerprint(participationType, body)
        registrationRequestRepository.findById(requestId).orElse(null)?.let {
            if (it.fingerprint != fingerprint) {
                throw ExpectedException(HttpStatus.CONFLICT, "같은 요청 키로 다른 내용의 등록을 보낼 수 없습니다.")
            }
            val participantIds =
                it.participantIds?.let { json -> jsonMapper.readValue(json, object : TypeReference<List<Long>>() {}) }
                    ?: listOf(it.participantId)
            return Registration(it.participantId, it.phoneNumber, it.created, participantIds)
        }

        val registration = action()
        registrationRequestRepository.save(
            RegistrationRequest(
                requestId = requestId,
                participationType = participationType,
                expoId = expoId,
                fingerprint = fingerprint,
                participantId = registration.id,
                phoneNumber = registration.phoneNumber,
                created = registration.created,
                participantIds = jsonMapper.writeValueAsString(registration.participantIds),
            ),
        )
        return registration
    }

    private fun fingerprint(
        participationType: ParticipationType,
        body: Any,
    ): String {
        val source = "$participationType:${jsonMapper.writeValueAsString(body)}"
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
