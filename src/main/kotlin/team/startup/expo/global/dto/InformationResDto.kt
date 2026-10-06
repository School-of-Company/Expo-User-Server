package team.startup.expo.global.dto

import tools.jackson.databind.JsonNode

/**
 * 신청 폼이나 설문의 답변. [answers]는 저장된 JSON을 해석 없이 그대로 돌려준다. 신청 답변은 문항 제목, 설문 답변은
 * 문항 ID를 키로 쓴다. [questions]는 제출 당시 문항 스냅샷이고, 스냅샷 없이 저장된 행(지금은 모든 행)은 `null`이다.
 */
data class InformationResDto(
    val answers: JsonNode,
    val questions: JsonNode? = null,
)
