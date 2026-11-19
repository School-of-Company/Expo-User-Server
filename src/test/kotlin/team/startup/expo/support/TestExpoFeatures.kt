package team.startup.expo.support

import team.startup.expo.global.client.expo.ExpoFeature
import team.startup.expo.global.client.expo.ExpoFeatures

/** 단위 테스트용 기능 설정. 기본은 모두 켜짐이고 [off]로 준 키만 끈다. */
fun expoFeatures(vararg off: ExpoFeature): ExpoFeatures = ExpoFeatures(ExpoFeature.entries.associate { it.name to (it !in off) })
