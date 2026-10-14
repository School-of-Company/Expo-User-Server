package team.startup.expo.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.util.PhoneHash

class PhoneHashTests :
    BehaviorSpec({
        given("같은 키로 전화번호를 해시할 때") {
            `when`("같은 번호를 표기만 다르게 넣으면") {
                then("같은 값이다") {
                    val plain = PhoneHash.of("01012345678", "key-one")

                    PhoneHash.of("010-1234-5678", "key-one") shouldBe plain
                    PhoneHash.of(" 010 1234 5678 ", "key-one") shouldBe plain
                }
            }
            `when`("다른 번호를 넣으면") {
                then("다른 값이다") {
                    (PhoneHash.of("01012345678", "key-one") == PhoneHash.of("01012345679", "key-one")) shouldBe false
                }
            }
            `when`("결과를 보면") {
                then("소문자 16진수 64자이고 번호 숫자가 그대로 드러나지 않는다") {
                    val hash = PhoneHash.of("01012345678", "key-one")

                    hash.length shouldBe 64
                    hash.all { it in '0'..'9' || it in 'a'..'f' } shouldBe true
                    hash shouldNotContain "01012345678"
                }
            }
        }

        given("키가 다를 때") {
            `when`("같은 번호를 해시하면") {
                then("값이 달라 키 없이는 번호를 대입해 되돌릴 수 없다") {
                    (PhoneHash.of("01012345678", "key-one") == PhoneHash.of("01012345678", "key-two")) shouldBe false
                }
            }
        }

        given("번호가 올바르지 않을 때") {
            `when`("숫자가 없으면") {
                then("ExpectedException이다") {
                    shouldThrow<ExpectedException> { PhoneHash.of("전화번호", "key-one") }
                }
            }
        }
    })
