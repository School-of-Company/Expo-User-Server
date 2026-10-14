package team.startup.expo.domain.auth.service

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import team.startup.expo.global.security.InternalProperties
import team.startup.expo.global.security.ParticipantSessionProperties
import team.startup.expo.global.util.PhoneHash

/**
 * 전화 인증에 성공한 번호를 박람회별로 한 번만 기록한다. 같은 번호가 다시 인증해도 건수는 늘지 않는다. 번호는 원문이 아니라
 * 키 있는 해시([PhoneHash])로만 저장한다. 집계용 기록이라 저장에 실패해도 인증 자체는 막지 않고 경고만 남긴다(번호는 로그에 남기지 않는다).
 */
@Component
class PhoneVerificationRecorder(
    private val jdbcTemplate: JdbcTemplate,
    sessionProperties: ParticipantSessionProperties,
    internalProperties: InternalProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 키를 따로 정하지 않았으면 내부 토큰에서 용도를 구분한 값을 파생해 쓴다
    private val key = sessionProperties.phoneHashKey.ifBlank { "phone-verification:${internalProperties.token}" }

    fun record(
        expoId: String,
        phoneNumber: String,
    ) {
        try {
            jdbcTemplate.update(
                "INSERT INTO tb_participant_phone_verification (expo_id, phone_hash, first_verified_at) " +
                    "VALUES (?, ?, (now() AT TIME ZONE 'UTC')) ON CONFLICT (expo_id, phone_hash) DO NOTHING",
                expoId,
                PhoneHash.of(phoneNumber, key),
            )
        } catch (exception: Exception) {
            log.warn("전화 인증 기록에 실패했습니다: expoId={}", expoId, exception)
        }
    }
}
