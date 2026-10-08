package team.startup.expo.security

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import team.startup.expo.domain.auth.presentation.dto.request.SignInReqDto
import team.startup.expo.domain.auth.presentation.dto.request.SignUpReqDto
import team.startup.expo.domain.auth.presentation.dto.response.TokenResDto
import team.startup.expo.global.security.InternalProperties
import team.startup.expo.global.security.jwt.JwtProperties
import team.startup.expo.support.IntegrationTestSupport
import java.time.LocalDateTime

/** 비밀번호, 토큰, 개인키가 로그나 `toString()`으로 새지 않는지 확인한다. */
@ExtendWith(OutputCaptureExtension::class)
class SensitiveLoggingTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `객체를 문자열로 만들어도 비밀 값은 드러나지 않는다`() {
        val texts =
            listOf(
                JwtProperties(privateKey = "PRIVATE-KEY-SECRET").toString(),
                InternalProperties(token = INTERNAL_SECRET).toString(),
                SignUpReqDto("관리자", "admin1", "a@b.com", "Passw0rd!-SECRET", "01012341234").toString(),
                SignInReqDto("admin1", "Passw0rd!-SECRET").toString(),
                TokenResDto("ACCESS-TOKEN-SECRET", "REFRESH-TOKEN-SECRET", LocalDateTime.now(), LocalDateTime.now()).toString(),
            ).joinToString("\n")

        listOf("PRIVATE-KEY-SECRET", INTERNAL_SECRET, "Passw0rd!-SECRET", "ACCESS-TOKEN-SECRET", "REFRESH-TOKEN-SECRET").forEach {
            texts.contains(it) shouldBe false
        }
    }

    @Test
    fun `검증에 실패한 요청의 비밀번호는 로그에 남지 않는다`(output: CapturedOutput) {
        mockMvc.perform(
            post("/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"관리자","nickname":"n","email":"a@b.com","password":"weak-LOG-SECRET","phoneNumber":"01012341234"}"""),
        )
        mockMvc.perform(
            post("/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nickname":"nobody","password":"wrong-LOG-SECRET"}"""),
        )

        output.all.contains("weak-LOG-SECRET") shouldBe false
        output.all.contains("wrong-LOG-SECRET") shouldBe false
    }

    @Test
    fun `틀린 내부 토큰과 개인키는 로그에 남지 않는다`(output: CapturedOutput) {
        mockMvc.perform(
            post("/internal/standard-participants/resolve")
                .header("X-Internal-Token", "wrong-INTERNAL-LOG-SECRET")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"expoId":"e","phoneNumber":"01012341234"}"""),
        )

        output.all.contains("wrong-INTERNAL-LOG-SECRET") shouldBe false
        output.all.contains("BEGIN PRIVATE KEY") shouldBe false
        output.all.contains(INTERNAL_TOKEN) shouldBe false
    }

    private companion object {
        const val INTERNAL_SECRET = "internal-secret-0123456789abcdef-xxxxxxxx"
    }
}
