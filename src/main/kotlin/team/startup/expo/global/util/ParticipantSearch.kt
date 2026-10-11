package team.startup.expo.global.util

/** 참가자 검색어를 `LIKE` 패턴과 번호 숫자로 바꾼다. 검색어가 비어 있으면 둘 다 빈 문자열이라 조건을 쓰지 않는다. */
object ParticipantSearch {
    const val ESCAPE = '!'

    /** 이름의 대소문자 무시 부분 일치 패턴. 검색어의 `%`, `_`, 이스케이프 문자는 와일드카드가 아닌 글자로 취급한다. */
    fun namePattern(keyword: String): String {
        if (keyword.isEmpty()) return ""
        val escaped = keyword.lowercase().flatMap { if (it == '%' || it == '_' || it == ESCAPE) listOf(ESCAPE, it) else listOf(it) }
        return "%${escaped.joinToString("")}%"
    }

    /** 숫자와 하이픈·공백뿐인 검색어만 번호 검색으로 보고 하이픈과 공백을 뺀 숫자를 돌려준다. 그 외에는 빈 문자열이다. */
    fun digits(keyword: String): String {
        val stripped = keyword.filterNot { it == '-' || it.isWhitespace() }
        return if (stripped.isNotEmpty() && stripped.all { it in '0'..'9' }) stripped else ""
    }
}
