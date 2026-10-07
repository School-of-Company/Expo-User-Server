package team.startup.expo.global.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * 참가자 QR에 ID와 함께 담는 추측하기 어려운 값. 참가자 ID는 순차라 ID만으로는 남의 QR을 만들 수 있다.
 * 16바이트 난수를 base64url(패딩 없이 22자)로 쓴다.
 */
object ParticipantCode {
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    fun generate(): String = encoder.encodeToString(ByteArray(16).also(random::nextBytes))

    /** 값을 비교할 때 걸린 시간으로 앞부분이 맞는지 알아내지 못하도록 일정한 시간에 비교한다. */
    fun matches(
        expected: String,
        actual: String,
    ): Boolean = MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), actual.toByteArray(Charsets.UTF_8))
}
