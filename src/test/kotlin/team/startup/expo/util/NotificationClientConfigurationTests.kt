package team.startup.expo.util

import feign.RequestTemplate
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import team.startup.expo.global.client.notification.NotificationClientConfiguration
import team.startup.expo.global.client.notification.NotificationClientProperties

class NotificationClientConfigurationTests :
    BehaviorSpec({
        fun headersSentWith(token: String): Map<String, Collection<String>> {
            val template = RequestTemplate()
            NotificationClientConfiguration()
                .notificationInternalTokenInterceptor(
                    NotificationClientProperties(internalToken = token),
                ).apply(template)
            return template.headers()
        }

        given("Notification 내부 토큰 설정이") {
            `when`("있으면") {
                then("X-Internal-Token 헤더로 보낸다") {
                    val token = "n".repeat(32)

                    headersSentWith(token)["X-Internal-Token"] shouldBe listOf(token)
                }
            }
            `when`("비어 있으면") {
                then("헤더를 보내지 않아 공개 요청과 같은 상한을 쓴다") {
                    headersSentWith("").containsKey("X-Internal-Token") shouldBe false
                }
            }
            `when`("32자보다 짧으면") {
                then("기동 시점에 거부하고 값을 노출하지 않는다") {
                    val secret = "short-SECRET-VALUE"

                    val exception = shouldThrow<IllegalArgumentException> { NotificationClientProperties(internalToken = secret) }

                    exception.message.orEmpty() shouldNotContain secret
                }
            }
        }
    })
