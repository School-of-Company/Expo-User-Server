package team.startup.expo.security

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import team.startup.expo.global.security.SignInProperties
import java.time.Duration

class SignInPropertiesTests :
    BehaviorSpec({
        given("로그인 제한 설정") {
            `when`("아무것도 정하지 않으면") {
                val properties = SignInProperties()

                then("15분 안에 5번 실패하면 막는 기본값이다") {
                    properties.maxFailures shouldBe 5
                    properties.lockDuration shouldBe Duration.ofMinutes(15)
                }
            }
            `when`("횟수가 0 이하이거나 시간이 0 이하이면") {
                then("기동하지 못하게 거부한다") {
                    shouldThrow<IllegalArgumentException> { SignInProperties(maxFailures = 0) }
                    shouldThrow<IllegalArgumentException> { SignInProperties(lockDuration = Duration.ZERO) }
                    shouldThrow<IllegalArgumentException> { SignInProperties(lockDuration = Duration.ofSeconds(-1)) }
                }
            }
        }
    })
