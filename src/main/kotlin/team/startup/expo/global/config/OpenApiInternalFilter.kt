package team.startup.expo.global.config

import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.OpenAPI

/** 문서에서 `/internal` 하위 경로와 그 경로에서만 쓰던 스키마, 태그, 인증 방식을 뺀다. */
object OpenApiInternalFilter {
    private const val INTERNAL_PREFIX = "/internal"
    private val SCHEMA_REF = Regex("#/components/schemas/([^\"]+)")

    fun remove(openApi: OpenAPI) {
        openApi.paths?.keys?.removeIf { it == INTERNAL_PREFIX || it.startsWith("$INTERNAL_PREFIX/") }
        removeUnusedSchemas(openApi)
        removeUnusedTags(openApi)
        openApi.components?.securitySchemes?.remove(SwaggerConfig.INTERNAL_TOKEN)
    }

    /** 남은 경로에서 닿을 수 있는 스키마만 남긴다. 스키마가 다른 스키마를 가리키는 것도 따라간다. */
    private fun removeUnusedSchemas(openApi: OpenAPI) {
        val schemas = openApi.components?.schemas ?: return
        val reachable = mutableSetOf<String>()
        val pending = ArrayDeque(referencedSchemas(Json.pretty(openApi.paths)))
        while (pending.isNotEmpty()) {
            val name = pending.removeFirst()
            if (!reachable.add(name)) continue
            schemas[name]?.let { pending.addAll(referencedSchemas(Json.pretty(it))) }
        }
        schemas.keys.removeIf { it !in reachable }
    }

    private fun referencedSchemas(json: String): List<String> = SCHEMA_REF.findAll(json).map { it.groupValues[1] }.toList()

    private fun removeUnusedTags(openApi: OpenAPI) {
        val used =
            openApi.paths
                ?.values
                ?.flatMap { it.readOperations() }
                ?.flatMap { it.tags.orEmpty() }
                ?.toSet()
                .orEmpty()
        openApi.tags = openApi.tags?.filter { it.name in used }
    }
}
