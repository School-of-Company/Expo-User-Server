package team.startup.expo.global.util

import org.springframework.http.HttpStatus
import team.startup.expo.domain.participation.entity.Occupation
import team.startup.expo.domain.participation.entity.Region
import team.startup.expo.global.exception.ExpectedException
import tools.jackson.databind.JsonNode

/** 신청 답변에서 꺼낸 동행자 한 명. */
data class CompanionInfo(
    val name: String,
    val occupation: Occupation,
    val region: Region,
    val school: String?,
) {
    /** 같은 대표자 밑에서 이름·구분·소속이 모두 같으면 같은 사람이다. 지역은 비교하지 않는다. */
    val key: List<Any?> get() = listOf(name, occupation, school)
}

/**
 * 신청 답변(`informationJson`)에서 동행자 문항(`formType == "COMPANION"`)의 답을 꺼낸다. 답변은 문항 제목을 키로 하고
 * 동행자는 `[{name, occupation, region, school?}]`이다. 문항 스냅샷이 없으면 어느 문항이 동행자인지 알 수 없어 동행자가 없는 것으로 본다.
 * 같은 사람이 한 요청에 여럿 있으면 한 명으로 센다.
 */
object Companions {
    const val MAX_PARTICIPANTS = 5
    private const val FORM_TYPE = "COMPANION"

    fun extract(
        informationJson: String?,
        questions: JsonNode?,
    ): List<CompanionInfo> {
        if (informationJson.isNullOrBlank() || questions == null || !questions.isArray) return emptyList()
        val titles = questions.filter { it.path("formType").asString() == FORM_TYPE }.map { it.path("title").asString() }
        if (titles.isEmpty()) return emptyList()
        val answers = InformationJson.toNode(informationJson)
        return titles
            .flatMap { title ->
                answers
                    .path(title)
                    .takeIf { it.isArray }
                    ?.toList()
                    .orEmpty()
            }.map(::parse)
            .distinctBy { it.key }
    }

    private fun parse(node: JsonNode): CompanionInfo {
        val name = node.path("name").asString().trim()
        if (name.isEmpty() || name.length > MAX_NAME_LENGTH) throw invalid("동행자 이름은 1~10자여야 합니다.")
        val occupation =
            Occupation.entries.firstOrNull { it.name == node.path("occupation").asString() } ?: throw invalid("동행자 구분이 올바르지 않습니다.")
        val region = Region.entries.firstOrNull { it.name == node.path("region").asString() } ?: throw invalid("동행자 지역이 올바르지 않습니다.")
        val school =
            node
                .path("school")
                .asString()
                .trim()
                .takeIf { it.isNotEmpty() }
        if (school != null && school.length > MAX_SCHOOL_LENGTH) throw invalid("동행자 소속은 100자 이하여야 합니다.")
        return CompanionInfo(name, occupation, region, school)
    }

    private fun invalid(message: String) = ExpectedException(HttpStatus.BAD_REQUEST, message)

    private const val MAX_NAME_LENGTH = 10
    private const val MAX_SCHOOL_LENGTH = 100
}
