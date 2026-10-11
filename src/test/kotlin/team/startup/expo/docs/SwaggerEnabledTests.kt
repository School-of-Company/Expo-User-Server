package team.startup.expo.docs

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.support.IntegrationTestSupport
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** `SWAGGER_ENABLED`를 켜면 문서가 열리고, 공개 API와 `/internal` 하위 경로가 그룹으로 나뉜다. */
@TestPropertySource(properties = ["springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"])
class SwaggerEnabledTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    private val mapper = JsonMapper.builder().build()

    @Test
    fun `문서를 켜면 공개 그룹과 내부 그룹 문서가 인증 없이 열린다`() {
        mockMvc.perform(get("/v3/api-docs/public")).andExpect(status().isOk)
        mockMvc.perform(get("/v3/api-docs/internal")).andExpect(status().isOk)
        // 문서 화면 진입 경로도 막히지 않는다(401이나 403이 아니다)
        val ui =
            mockMvc
                .perform(get("/swagger-ui.html"))
                .andReturn()
                .response.status
        check(ui != 401 && ui != 403) { "swagger-ui.html이 막혀 있다: $ui" }
    }

    @Test
    fun `공개 그룹에는 internal 경로가 없고 내부 그룹에는 internal 경로만 있다`() {
        val publicPaths = paths("public")
        val internalPaths = paths("internal")

        publicPaths shouldContain "/auth"
        publicPaths shouldContain "/participant-auth/verify"
        publicPaths.none { it.startsWith("/internal") } shouldBe true
        internalPaths shouldContain "/internal/standard-participants/verify"
        internalPaths shouldContain "/internal/participant-sessions/resolve"
        internalPaths.all { it.startsWith("/internal") } shouldBe true
        internalPaths shouldNotContain "/auth"
    }

    @Test
    fun `문서에 인증 방식과 컨트롤러 태그가 표시된다`() {
        val publicDoc = doc("public")
        val internalDoc = doc("internal")

        val schemes = publicDoc.path("components").path("securitySchemes")
        (schemes.has("bearerAuth") && schemes.has("internalToken") && schemes.has("participantSession")) shouldBe true
        // 내부 API는 X-Internal-Token, 관리자 API는 JWT를 요구한다고 표시된다
        internalDoc
            .path("paths")
            .path("/internal/standard-participants/verify")
            .path("post")
            .path("security")
            .toString() shouldBe """[{"internalToken":[]}]"""
        publicDoc
            .path("paths")
            .path("/admin/my")
            .path("get")
            .path("security")
            .toString() shouldBe """[{"bearerAuth":[]}]"""
        publicDoc
            .path("paths")
            .path("/auth/signin")
            .path("post")
            .has("security") shouldBe false
        publicDoc
            .path("paths")
            .path("/auth")
            .path("post")
            .path("tags")[0]
            .asString() shouldBe "관리자 인증"
    }

    private fun doc(group: String): JsonNode =
        mapper.readTree(
            mockMvc
                .perform(get("/v3/api-docs/$group"))
                .andReturn()
                .response.contentAsString,
        )

    private fun paths(group: String): List<String> = doc(group).path("paths").propertyNames().toList()
}
