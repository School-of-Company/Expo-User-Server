package team.startup.expo.util

import io.kotest.matchers.shouldBe
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.tags.Tag
import org.junit.jupiter.api.Test
import team.startup.expo.global.config.OpenApiInternalFilter

class OpenApiInternalFilterTests {
    @Test
    fun `internal 경로와 그 경로에서만 쓰는 스키마와 태그와 인증 방식을 뺀다`() {
        val openApi = openApi()

        OpenApiInternalFilter.remove(openApi)

        openApi.paths.keys.toList() shouldBe listOf("/admin")
        // 공개 경로가 쓰는 스키마와 그 스키마가 가리키는 스키마는 남고, 내부에서만 쓰던 스키마는 빠진다
        openApi.components.schemas.keys
            .toSet() shouldBe setOf("PublicRes", "Nested")
        openApi.tags.map { it.name } shouldBe listOf("관리자")
        openApi.components.securitySchemes.keys
            .toSet() shouldBe setOf("bearerAuth")
    }

    @Test
    fun `internal로 시작하는 다른 경로 이름은 빼지 않는다`() {
        val openApi = openApi()
        openApi.paths.addPathItem("/internals", pathItem(null, "관리자"))

        OpenApiInternalFilter.remove(openApi)

        openApi.paths.keys.toList() shouldBe listOf("/admin", "/internals")
    }

    private fun openApi(): OpenAPI =
        OpenAPI()
            .paths(
                Paths()
                    .addPathItem("/admin", pathItem("PublicRes", "관리자"))
                    .addPathItem("/internal/x", pathItem("InternalRes", "내부: 참가자")),
            ).components(
                Components()
                    .addSchemas("PublicRes", ObjectSchema().addProperty("n", Schema<Any>().`$ref`("#/components/schemas/Nested")))
                    .addSchemas("Nested", ObjectSchema())
                    .addSchemas("InternalRes", ObjectSchema())
                    .addSecuritySchemes("bearerAuth", SecurityScheme())
                    .addSecuritySchemes("internalToken", SecurityScheme()),
            ).tags(listOf(Tag().name("관리자"), Tag().name("내부: 참가자")))

    private fun pathItem(
        schema: String?,
        tag: String,
    ): PathItem {
        val response =
            ApiResponse().description("ok").also {
                if (schema != null) {
                    it.content =
                        io.swagger.v3.oas.models.media.Content().addMediaType(
                            "application/json",
                            io.swagger.v3.oas.models.media
                                .MediaType()
                                .schema(Schema<Any>().`$ref`("#/components/schemas/$schema")),
                        )
                }
            }
        return PathItem().get(Operation().addTagsItem(tag).responses(ApiResponses().addApiResponse("200", response)))
    }
}
