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
     * 저장된 JSON을 응답용 노드로 읽는다. 값이 없거나 읽을 수 없으면 빈 객체이다. 저장 방식에 따라 JSON이 한 번 더
     * 문자열로 감싸여 있을 수 있어, 그 안쪽이 객체나 배열일 때만 풀어서 돌려준다. 안쪽이 그 밖의 값이면 원래 JSON
     * 문자열 값(`"광주"`, `"123"`)이므로 타입이나 내용을 바꾸지 않고 그대로 둔다.
     */
    fun toNode(json: String?): JsonNode {
        if (json.isNullOrBlank()) return mapper.createObjectNode()
        val node =
            try {
                mapper.readTree(json)
            } catch (_: JacksonException) {
                return mapper.createObjectNode()
            }
        return if (node.isString) unwrapSerialized(node) else node
    }

    private fun unwrapSerialized(node: JsonNode): JsonNode {
        val inner =
            try {
                mapper.readTree(node.asString())
            } catch (_: JacksonException) {
                return node
            }
        return if (inner.isObject || inner.isArray) inner else node
    }

    /** 저장된 문항 스냅샷을 응답용 노드로 읽는다. 스냅샷 없이 저장된 행(`null`)이거나 배열로 읽을 수 없으면 `null`이다. */
    fun toQuestionsNode(json: String?): JsonNode? {
        if (json.isNullOrBlank()) return null
        val node = toNode(json)
        return node.takeIf { it.isArray }
    }
}
