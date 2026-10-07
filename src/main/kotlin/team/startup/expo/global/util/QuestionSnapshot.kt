package team.startup.expo.global.util

import tools.jackson.databind.JsonNode

/**
 * 제출 당시 문항 스냅샷(`questions`)을 다룬다. 이 서비스는 문항을 해석하지 않고 그대로 보존하므로 문항 하나하나의
 * 필드(`id`, `title`, `order`, `formType`, `jsonData`, `otherJson`, `dynamicFormType`)는 검사하지 않는다. 호출자가
 * 필드를 추가해도 받아 둘 수 있게 하고, 객체의 배열이 아닌 값이나 비정상적으로 큰 값만 거른다.
 */
object QuestionSnapshot {
    private const val MAX_QUESTIONS = 1_000

    /** 스냅샷이 올바르지 않으면 이유를, 없거나 올바르면 `null`을 돌려준다. 스냅샷이 없는 것은 오류가 아니다. */
    fun errorOf(questions: JsonNode?): String? {
        if (questions == null || questions.isNull) return null
        if (!questions.isArray) return "questions는 배열이어야 합니다."
        if (questions.size() > MAX_QUESTIONS) return "questions는 최대 ${MAX_QUESTIONS}개입니다."
        if (questions.any { !it.isObject }) return "questions의 각 문항은 객체여야 합니다."
        return null
    }

    /** 저장할 JSON 문자열. 스냅샷이 없으면 `null`이라 스냅샷 없이 저장된 행과 구분된다. */
    fun serialize(questions: JsonNode?): String? = if (questions == null || questions.isNull) null else questions.toString()
}
