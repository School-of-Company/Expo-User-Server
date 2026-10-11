package team.startup.expo.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import team.startup.expo.global.util.ParticipantSearch

class ParticipantSearchTests {
    @Test
    fun `빈 검색어는 조건을 쓰지 않는다`() {
        ParticipantSearch.namePattern("") shouldBe ""
        ParticipantSearch.digits("") shouldBe ""
    }

    @Test
    fun `이름 패턴은 소문자이고 와일드카드 글자를 이스케이프한다`() {
        ParticipantSearch.namePattern("Kim") shouldBe "%kim%"
        ParticipantSearch.namePattern("100%_!") shouldBe "%100!%!_!!%"
    }

    @Test
    fun `숫자와 하이픈과 공백뿐인 검색어만 번호로 본다`() {
        ParticipantSearch.digits("010-1234") shouldBe "0101234"
        ParticipantSearch.digits(" 010 1234 ") shouldBe "0101234"
        ParticipantSearch.digits("홍길동") shouldBe ""
        ParticipantSearch.digits("홍1") shouldBe ""
        ParticipantSearch.digits("-") shouldBe ""
    }
}
