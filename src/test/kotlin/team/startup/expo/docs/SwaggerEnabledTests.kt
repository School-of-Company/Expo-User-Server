package team.startup.expo.docs

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.support.IntegrationTestSupport
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** `SWAGGER_ENABLED`만 켜면 `/auth/v3/api-docs`가 열리고 `/internal` 하위 경로는 문서에서 빠진다. */
@TestPropertySource(properties = ["springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"])
class SwaggerEnabledTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    private val mapper = JsonMapper.builder().build()

    @Test
    fun `문서를 켜면 Gateway 접두사 아래 문서가 인증 없이 열리고 예전 경로는 닫혀 있다`() {
        mockMvc.perform(get("/auth/v3/api-docs")).andExpect(status().isOk)
        // 문서 화면 진입 경로도 막히지 않는다(401이나 403이 아니다)
        val ui =
            mockMvc
                .perform(get("/auth/swagger-ui.html"))
                .andReturn()
                .response.status
        check(ui != 401 && ui != 403) { "swagger-ui.html이 막혀 있다: $ui" }
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `문서 경로는 GET만 열린다`() {
        mockMvc.perform(post("/auth/v3/api-docs")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `기본으로 internal 경로와 그 경로의 인증 방식과 스키마가 문서에서 빠진다`() {
        val doc = doc()
        val paths = doc.path("paths").propertyNames().toList()

        paths shouldContain "/auth"
        paths shouldContain "/participant-auth/verify"
        paths.none { it.startsWith("/internal") } shouldBe true
        val schemes = doc.path("components").path("securitySchemes")
        (schemes.has("bearerAuth") && schemes.has("participantSession")) shouldBe true
        schemes.has("internalToken") shouldBe false
        // 내부 API에서만 쓰던 응답 스키마는 남지 않는다
        doc.path("components").path("schemas").has("CancellationStatusResDto") shouldBe false
        // 내부 API만 쓰던 태그도 남지 않는다
        doc.path("tags").none { it.path("name").asString().startsWith("내부") } shouldBe true
    }

    @Test
    fun `공개 API의 인증 방식과 태그가 표시된다`() {
        val doc = doc()

        doc
            .path("paths")
            .path("/admin/my")
            .path("get")
            .path("security")
            .toString() shouldBe """[{"bearerAuth":[]}]"""
        doc
            .path("paths")
            .path("/auth/signin")
            .path("post")
            .has("security") shouldBe false
        doc
            .path("paths")
            .path("/auth")
            .path("post")
            .path("tags")[0]
            .asString() shouldBe "관리자 인증"
    }

    @Test
    fun `공개 목록 API의 쿼리 DTO가 쿼리 파라미터로 펼쳐진다`() {
        val names =
            doc()
                .path("paths")
                .path("/participant/{expo_id}")
                .path("get")
                .path("parameters")
                .filter { it.path("in").asString() == "query" }
                .map { it.path("name").asString() }

        names shouldContain "page"
    }

    @Test
    fun `servers는 요청의 Host와 상관없이 상대 경로로 고정된다`() {
        val servers =
            mapper
                .readTree(
                    mockMvc
                        .perform(get("/auth/v3/api-docs").header("Host", "10.0.0.5:18081"))
                        .andReturn()
                        .response.contentAsString,
                ).path("servers")

        servers.toString() shouldBe """[{"url":"/"}]"""
    }

    private fun doc(): JsonNode =
        mapper.readTree(
            mockMvc
                .perform(get("/auth/v3/api-docs"))
                .andReturn()
                .response.contentAsString,
        )
}
