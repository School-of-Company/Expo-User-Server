package team.startup.expo.global.util

import org.springframework.http.HttpStatus
import team.startup.expo.global.exception.ExpectedException
import tools.jackson.core.JacksonException
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
}
