package team.startup.expo.domain.training.entity

import team.startup.expo.global.client.expo.ExpoFeature

enum class ApplicationType(
    val koreanName: String,
    /** 이 구분으로 새로 등록할 때 켜져 있어야 하는 박람회 기능. */
    val registrationFeature: ExpoFeature,
) {
    PRE("사전 등록", ExpoFeature.PRE_REGISTRATION),
    FIELD("현장 등록", ExpoFeature.FIELD_REGISTRATION),
}
