package team.startup.expo.global.client.expo

import org.springframework.http.HttpStatus
import team.startup.expo.global.exception.ExpectedException

/** 이 서비스가 검사하는 Expo 기능 키. 이름이 Expo 카탈로그의 `key`와 같다(Expo `docs/expo-feature-settings.md`). */
enum class ExpoFeature(
    val label: String,
) {
    PRE_REGISTRATION("사전등록"),
    FIELD_REGISTRATION("현장등록"),
    COMPANION_REGISTRATION("동행자 등록"),
    TRAINING_PROGRAM("연수 프로그램"),
}

/**
 * Expo `GET /internal/expo/{expo_id}`의 `features`에서 읽은 키별 실효 값(`effectiveEnabled`). 신규 쓰기 직전에 [require]로 검사한다.
 * 꺼진 기능은 409(`FEATURE_DISABLED`), 응답에 없는 키는 구형 Expo 응답이라 허용으로 보지 않고 503이다.
 *
 * 조회가 실패했으면 [failure](503, 없는 박람회는 404)를 담아 두고 [require]에서 던진다. 재시도 응답·기존 연수자 반환·삭제 중 409처럼
 * [require]까지 가지 않는 경로는 Expo 장애에 막히지 않는다.
 */
class ExpoFeatures(
    private val effective: Map<String, Boolean>,
    private val failure: ExpectedException? = null,
) {
    fun require(feature: ExpoFeature) {
        failure?.let { throw it }
        when (effective[feature.name]) {
            true -> Unit
            false -> throw ExpectedException(HttpStatus.CONFLICT, "${feature.label} 기능을 사용하지 않는 박람회입니다.", code = "FEATURE_DISABLED")
            null -> throw unavailable()
        }
    }

    companion object {
        fun unavailable() = ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "박람회 기능 설정을 가져올 수 없습니다.")
    }
}
