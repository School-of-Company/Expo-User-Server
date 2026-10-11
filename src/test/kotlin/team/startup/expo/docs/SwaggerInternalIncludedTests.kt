package team.startup.expo.docs

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import team.startup.expo.support.IntegrationTestSupport
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** `SWAGGER_INCLUDE_INTERNAL`까지 켜면 `/internal` 하위 경로와 `X-Internal-Token` 인증 방식이 문서에 나온다. */
@TestPropertySource(
    properties = ["springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true", "swagger.include-internal=true"],
)
class SwaggerInternalIncludedTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    private val mapper = JsonMapper.builder().build()

    @Test
    fun `내부 경로와 internalToken 인증 방식과 스키마가 문서에 나온다`() {
        val doc = doc()
        val paths = doc.path("paths").propertyNames().toList()

        paths shouldContain "/auth"
        paths shouldContain "/internal/standard-participants/verify"
        paths shouldContain "/internal/standard-participants/cancellation-status"
        doc.path("components").path("securitySchemes").has("internalToken") shouldBe true
        doc
            .path("paths")
            .path("/internal/standard-participants/cancellation-status")
            .path("post")
            .path("security")
            .toString() shouldBe """[{"internalToken":[]}]"""
        doc.path("components").path("schemas").has("CancellationStatusResDto") shouldBe true
    }

    @Test
    fun `내부 목록 API의 쿼리 조건이 쿼리 파라미터로 나온다`() {
        val doc = doc()

        queryParameters(doc, "/internal/expos/{expoId}/standard-participants/details").let {
            it shouldContain "q"
            it shouldContain "occupation"
            it shouldContain "cursor"
        }
        queryParameters(doc, "/internal/expos/{expoId}/trainees/details") shouldContain "size"
    }

    private fun queryParameters(
        doc: JsonNode,
        path: String,
    ): List<String> =
        doc
            .path("paths")
            .path(path)
            .path("get")
            .path("parameters")
            .filter { it.path("in").asString() == "query" }
            .map { it.path("name").asString() }

    private fun doc(): JsonNode =
        mapper.readTree(
            mockMvc
                .perform(get("/auth/v3/api-docs"))
                .andReturn()
                .response.contentAsString,
        )
}
