package team.startup.expo.util

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import team.startup.expo.global.util.ParticipantSearch

class ParticipantSearchTests :
    BehaviorSpec({
        given("검색어가 비어 있을 때") {
            `when`("이름 패턴과 번호 숫자를 만들면") {
                then("둘 다 빈 문자열이라 조건을 쓰지 않는다") {
                    ParticipantSearch.namePattern("") shouldBe ""
                    ParticipantSearch.digits("") shouldBe ""
                }
            }
        }

        given("이름 검색어가 있을 때") {
            `when`("이름 패턴을 만들면") {
                then("소문자로 바꾼다") {
                    ParticipantSearch.namePattern("Kim") shouldBe "%kim%"
                }
                then("퍼센트와 밑줄과 이스케이프 문자를 와일드카드가 아닌 글자로 이스케이프한다") {
                    ParticipantSearch.namePattern("100%_!") shouldBe "%100!%!_!!%"
                }
            }
        }

        given("번호 검색어일 때") {
            `when`("숫자와 하이픈과 공백뿐이면") {
                then("하이픈과 공백을 뺀 숫자를 돌려준다") {
                    ParticipantSearch.digits("010-1234") shouldBe "0101234"
                    ParticipantSearch.digits(" 010 1234 ") shouldBe "0101234"
                }
            }
            `when`("숫자가 아닌 글자가 섞여 있거나 구분자뿐이면") {
                then("번호 검색으로 보지 않아 빈 문자열이다") {
                    ParticipantSearch.digits("홍길동") shouldBe ""
                    ParticipantSearch.digits("홍1") shouldBe ""
                    ParticipantSearch.digits("-") shouldBe ""
                }
            }
        }
    })
