package team.startup.expo.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import team.startup.expo.domain.participation.entity.Occupation
import team.startup.expo.domain.participation.entity.Region
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.util.Companions
import team.startup.expo.global.util.ParticipantCode
import tools.jackson.databind.json.JsonMapper

class CompanionsTests {
    private val mapper = JsonMapper.builder().build()

    private val questions =
        mapper.readTree(
            """[{"id":"1","title":"이름","formType":"SENTENCE"},{"id":"2","title":"동행자","formType":"COMPANION"}]""",
        )

    @Test
    fun `동행자 문항의 답에서 동행자를 꺼낸다`() {
        val json = """{"이름":"홍","동행자":[{"name":"밥","occupation":"GENERAL","region":"JEONNAM","school":" 광주고 "}]}"""

        val companions = Companions.extract(json, questions)

        companions.size shouldBe 1
        companions[0].name shouldBe "밥"
        companions[0].occupation shouldBe Occupation.GENERAL
        companions[0].region shouldBe Region.JEONNAM
        companions[0].school shouldBe "광주고"
    }

    @Test
    fun `스냅샷이 없거나 동행자 문항이 없으면 동행자가 없는 것으로 본다`() {
        val json = """{"동행자":[{"name":"밥","occupation":"GENERAL","region":"OTHER"}]}"""

        Companions.extract(json, null) shouldBe emptyList()
        Companions.extract(json, mapper.readTree("""[{"title":"동행자","formType":"SENTENCE"}]""")) shouldBe emptyList()
        Companions.extract(null, questions) shouldBe emptyList()
        Companions.extract("""{"이름":"홍"}""", questions) shouldBe emptyList()
    }

    @Test
    fun `이름과 구분과 소속이 모두 같으면 한 명으로 센다`() {
        val json =
            """{"동행자":[
                {"name":"밥","occupation":"GENERAL","region":"OTHER"},
                {"name":"밥","occupation":"GENERAL","region":"GWANGJU"},
                {"name":"밥","occupation":"GENERAL","region":"OTHER","school":"광주고"}
            ]}"""

        Companions.extract(json, questions).map { it.school } shouldBe listOf(null, "광주고")
    }

    @Test
    fun `올바르지 않은 동행자는 400이다`() {
        listOf(
            """{"name":"","occupation":"GENERAL","region":"OTHER"}""",
            """{"name":"열한글자이름이에요이게","occupation":"GENERAL","region":"OTHER"}""",
            """{"name":"밥","occupation":"모름","region":"OTHER"}""",
            """{"name":"밥","occupation":"GENERAL","region":"서울"}""",
        ).forEach {
            shouldThrow<ExpectedException> { Companions.extract("""{"동행자":[$it]}""", questions) }
        }
    }

    @Test
    fun `참가자 code는 22자 base64url이고 매번 다르다`() {
        val codes = List(100) { ParticipantCode.generate() }

        codes.toSet().size shouldBe 100
        codes.all { it.length == 22 && it.matches(Regex("[A-Za-z0-9_-]+")) } shouldBe true
    }

    @Test
    fun `code는 같은 값만 일치한다`() {
        val code = ParticipantCode.generate()

        ParticipantCode.matches(code, code) shouldBe true
        ParticipantCode.matches(code, ParticipantCode.generate()) shouldBe false
        ParticipantCode.matches(code, "") shouldBe false
    }
}
