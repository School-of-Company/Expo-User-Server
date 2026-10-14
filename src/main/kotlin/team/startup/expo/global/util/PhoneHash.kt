package team.startup.expo.global.util

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 전화번호를 원문 대신 저장할 키 있는 해시(HMAC-SHA256, 소문자 16진수 64자)로 바꾼다. 키가 없는 해시는 숫자가 한정된 전화번호를
 * 대입으로 되돌릴 수 있어서 키를 쓴다. 숫자만 남겨 해시하므로 하이픈 같은 표기 차이로 같은 번호가 둘로 세어지지 않는다.
 */
object PhoneHash {
    private const val ALGORITHM = "HmacSHA256"

    fun of(
        phoneNumber: String,
        key: String,
    ): String {
        val digits = PhoneNumbers.digitsOnly(phoneNumber)
        val mac = Mac.getInstance(ALGORITHM).apply { init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), ALGORITHM)) }
        return mac.doFinal(digits.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
