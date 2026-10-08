package team.startup.expo.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import team.startup.expo.global.util.InformationJson

class InformationJsonTests {
    @Test
    fun `값이 없거나 비어 있으면 빈 객체이다`() {
        InformationJson.toNode(null).toString() shouldBe "{}"
        InformationJson.toNode("").toString() shouldBe "{}"
        InformationJson.toNode("   ").toString() shouldBe "{}"
    }

    @Test
    fun `읽을 수 없는 값은 빈 객체이다`() {
        InformationJson.toNode("{깨진 json").toString() shouldBe "{}"
    }

    @Test
    fun `객체와 배열은 그대로 돌려준다`() {
        InformationJson.toNode("""{"이름":"홍길동","나이":20}""").toString() shouldBe """{"이름":"홍길동","나이":20}"""
        InformationJson.toNode("""["a","b"]""").toString() shouldBe """["a","b"]"""
    }

    @Test
    fun `한 번 더 문자열로 감싸인 객체와 배열은 풀어서 돌려준다`() {
        InformationJson.toNode(""""{\"이름\":\"홍길동\"}"""").toString() shouldBe """{"이름":"홍길동"}"""
        InformationJson.toNode(""""[\"a\",\"b\"]"""").toString() shouldBe """["a","b"]"""
    }

    @Test
    fun `JSON 문자열 값은 내용과 타입을 바꾸지 않는다`() {
        // 안쪽이 JSON으로 읽히지 않는 경우: 답변이 사라져 빈 객체가 되면 안 된다
        val text = InformationJson.toNode(""""광주"""")
        text.isString shouldBe true
        text.asString() shouldBe "광주"

        // 안쪽이 숫자로 읽히는 경우: 숫자로 바뀌면 안 된다
        val digits = InformationJson.toNode("""123""")
        digits.isNumber shouldBe true
        val quotedDigits = InformationJson.toNode(""""123"""")
        quotedDigits.isString shouldBe true
        quotedDigits.asString() shouldBe "123"
    }
}
