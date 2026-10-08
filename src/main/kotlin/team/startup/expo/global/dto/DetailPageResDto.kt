package team.startup.expo.global.dto

/** [nextCursor]는 다음 페이지를 읽을 때 `cursor`로 보낼 마지막 ID이고, 마지막 페이지면 `null`이다. */
data class DetailPageResDto<T>(
    val items: List<T>,
    val nextCursor: Long?,
)
