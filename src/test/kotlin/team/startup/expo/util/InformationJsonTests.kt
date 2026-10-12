package team.startup.expo.util

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import team.startup.expo.global.util.InformationJson

class InformationJsonTests :
    BehaviorSpec({
        given("값이 없거나 읽을 수 없을 때") {
            `when`("노드로 바꾸면") {
                then("null이거나 비어 있으면 빈 객체이다") {
                    InformationJson.toNode(null).toString() shouldBe "{}"
                    InformationJson.toNode("").toString() shouldBe "{}"
                    InformationJson.toNode("   ").toString() shouldBe "{}"
                }
                then("읽을 수 없는 값도 빈 객체이다") {
                    InformationJson.toNode("{깨진 json").toString() shouldBe "{}"
                }
            }
        }

        given("객체나 배열 JSON일 때") {
            `when`("노드로 바꾸면") {
                then("그대로 돌려준다") {
                    InformationJson.toNode("""{"이름":"홍길동","나이":20}""").toString() shouldBe """{"이름":"홍길동","나이":20}"""
                    InformationJson.toNode("""["a","b"]""").toString() shouldBe """["a","b"]"""
                }
            }
        }

        given("한 번 더 문자열로 감싸인 객체와 배열일 때") {
            `when`("노드로 바꾸면") {
                then("풀어서 돌려준다") {
                    InformationJson.toNode(""""{\"이름\":\"홍길동\"}"""").toString() shouldBe """{"이름":"홍길동"}"""
                    InformationJson.toNode(""""[\"a\",\"b\"]"""").toString() shouldBe """["a","b"]"""
                }
            }
        }

        given("JSON 문자열 값일 때") {
            `when`("안쪽이 JSON으로 읽히지 않으면") {
                then("답변이 사라지지 않고 문자열 그대로이다") {
                    val text = InformationJson.toNode(""""광주"""")
                    text.isString shouldBe true
                    text.asString() shouldBe "광주"
                }
            }
            `when`("안쪽이 숫자로 읽히면") {
                then("숫자로 바뀌지 않고 타입을 유지한다") {
                    InformationJson.toNode("""123""").isNumber shouldBe true
                    val quotedDigits = InformationJson.toNode(""""123"""")
                    quotedDigits.isString shouldBe true
                    quotedDigits.asString() shouldBe "123"
                }
            }
        }
    })
