package team.startup.expo.internal

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import team.startup.expo.global.security.InternalProperties

class InternalPropertiesTests {
    @Test
    fun `32자 이상의 토큰은 받는다`() {
        InternalProperties("a".repeat(32)).token.length shouldBe 32
    }

    @Test
    fun `짧은 토큰은 기동 시점에 거부하고 값을 노출하지 않는다`() {
        val secret = "short-SECRET-VALUE"

        val exception = assertThrows(IllegalArgumentException::class.java) { InternalProperties(secret) }

        (exception.message?.contains(secret) == true) shouldBe false
    }
}
