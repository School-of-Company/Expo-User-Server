package team.startup.expo.global.util

import org.springframework.http.HttpStatus
import team.startup.expo.global.exception.ExpectedException
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** `information_json`은 `jsonb`라서 JSON이 아닌 값은 저장 중에 DB 오류가 된다. 500이 되지 않도록 먼저 400으로 거른다. */
object InformationJson {
    private val mapper = JsonMapper.builder().build()

    fun requireValid(json: String?) {
        if (json == null) return
        try {
            mapper.readTree(json)
        } catch (_: JacksonException) {
            throw ExpectedException(HttpStatus.BAD_REQUEST, "informationJson이 올바른 JSON이 아닙니다.")
        }
    }

    /**
     * 저장된 JSON을 응답용 노드로 읽는다. 값이 없거나 읽을 수 없으면 빈 객체이다. 저장 방식에 따라 JSON 문자열이
     * 한 번 더 따옴표로 감싸여 있을 수 있어, 그런 경우에는 안쪽을 다시 읽는다.
     */
    fun toNode(json: String?): JsonNode {
        if (json.isNullOrBlank()) return mapper.createObjectNode()
        return try {
            val node = mapper.readTree(json)
            if (node.isString) mapper.readTree(node.asString()) else node
        } catch (_: JacksonException) {
            mapper.createObjectNode()
        }
    }
}
