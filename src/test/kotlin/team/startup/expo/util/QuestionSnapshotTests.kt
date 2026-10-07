package team.startup.expo.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import team.startup.expo.global.util.InformationJson
import team.startup.expo.global.util.QuestionSnapshot
import tools.jackson.databind.json.JsonMapper

class QuestionSnapshotTests {
    private val mapper = JsonMapper.builder().build()

    private fun node(json: String) = mapper.readTree(json)

    @Test
    fun `스냅샷이 없는 것은 오류가 아니고 저장도 null이다`() {
        QuestionSnapshot.errorOf(null) shouldBe null
        QuestionSnapshot.errorOf(node("null")) shouldBe null
        QuestionSnapshot.serialize(null) shouldBe null
        QuestionSnapshot.serialize(node("null")) shouldBe null
    }

    @Test
    fun `객체의 배열은 받고 문항의 필드는 검사하지 않는다`() {
        QuestionSnapshot.errorOf(node("[]")) shouldBe null
        QuestionSnapshot.errorOf(
            node("""[{"id":"1","title":"이름","order":0,"formType":"SENTENCE","jsonData":null,"otherJson":null}]"""),
        ) shouldBe
            null
        // 호출자가 필드를 추가해도 받아 둔다
        QuestionSnapshot.errorOf(node("""[{"id":"1","title":"이름","새필드":true}]""")) shouldBe null
    }

    @Test
    fun `배열이 아니거나 객체가 아닌 문항이 있으면 오류이다`() {
        (QuestionSnapshot.errorOf(node("""{"id":"1"}""")) != null) shouldBe true
        (QuestionSnapshot.errorOf(node(""""문자열"""")) != null) shouldBe true
        (QuestionSnapshot.errorOf(node("""["이름"]""")) != null) shouldBe true
        (QuestionSnapshot.errorOf(node("""[{"id":"1"}, 3]""")) != null) shouldBe true
    }

    @Test
    fun `문항 수에는 상한이 없어 큰 설문도 받는다`() {
        // Form은 문항 수를 제한하지 않으므로 정상 접수된 큰 설문을 이쪽에서 거부하면 접수 건이 끝나지 않는다
        val large = (1..5_000).joinToString(",", "[", "]") { """{"id":"$it"}""" }

        QuestionSnapshot.errorOf(node(large)) shouldBe null
        InformationJson.toQuestionsNode(QuestionSnapshot.serialize(node(large)))?.size() shouldBe 5_000
    }

    @Test
    fun `저장한 스냅샷은 읽을 때 같은 배열로 돌아온다`() {
        val stored = QuestionSnapshot.serialize(node("""[{"id":"1","title":"이름"}]"""))

        InformationJson.toQuestionsNode(stored).toString() shouldBe """[{"id":"1","title":"이름"}]"""
    }

    @Test
    fun `스냅샷이 없거나 배열로 읽을 수 없으면 읽을 때 null이다`() {
        InformationJson.toQuestionsNode(null) shouldBe null
        InformationJson.toQuestionsNode("") shouldBe null
        InformationJson.toQuestionsNode("""{"id":"1"}""") shouldBe null
        InformationJson.toQuestionsNode("{깨진 json") shouldBe null
    }
}
