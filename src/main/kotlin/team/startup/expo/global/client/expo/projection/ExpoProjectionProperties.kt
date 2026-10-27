package team.startup.expo.global.client.expo.projection

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * `enabled`가 `true`면 박람회 기간과 사전등록 회차를 Expo 호출 대신 로컬 프로젝션에서 읽는다. 프로젝션에 없는 박람회는
 * 기존처럼 Expo를 부른다. 끄면(기본) 프로젝션을 읽지 않아 지금과 똑같이 동작한다.
 */
@ConfigurationProperties(prefix = "expo-projection")
data class ExpoProjectionProperties(
    val enabled: Boolean = false,
)
