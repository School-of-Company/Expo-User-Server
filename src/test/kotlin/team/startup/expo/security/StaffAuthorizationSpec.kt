package team.startup.expo.security

import feign.FeignException
import feign.Request
import feign.Response
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockServletContext
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import team.startup.expo.domain.auth.presentation.StaffAuthController
import team.startup.expo.domain.auth.service.impl.StaffAuthServiceImpl
import team.startup.expo.domain.participation.presentation.BadgePrintController
import team.startup.expo.domain.participation.presentation.ParticipantController
import team.startup.expo.domain.participation.presentation.dto.request.ResolveBadgePrintJobReqDto
import team.startup.expo.domain.participation.service.BadgePrintService
import team.startup.expo.domain.training.presentation.TraineeController
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.entity.Authority
import team.startup.expo.domain.user.entity.Status
import team.startup.expo.domain.user.presentation.AdminController
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.global.client.expo.ExpoAuthorization
import team.startup.expo.global.client.expo.ExpoClient
import team.startup.expo.global.client.expo.StaffInviteExchangeResDto
import team.startup.expo.global.exception.GlobalExceptionHandler
import team.startup.expo.global.security.ExpoAccessConfiguration
import team.startup.expo.global.security.InternalProperties
import team.startup.expo.global.security.SecurityConfig
import team.startup.expo.global.security.StaffPrincipal
import team.startup.expo.global.security.jwt.JwtProperties
import team.startup.expo.global.security.jwt.JwtProvider
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.security.KeyPairGenerator
import java.time.Instant
import java.util.Base64
import java.util.Optional

class StaffAuthorizationSpec :
    BehaviorSpec({
        val context =
            AnnotationConfigWebApplicationContext().apply {
                servletContext = MockServletContext()
                register(StaffSecurityTestConfig::class.java)
                refresh()
            }
        afterSpec { context.close() }
        val mvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(
                    context.getBean(FilterChainProxy::class.java),
                ).build()
        val jwt = context.getBean(JwtProvider::class.java)
        val client = context.getBean(ExpoClient::class.java)
        val admins = context.getBean(AdminRepository::class.java)
        val badges = context.getBean(BadgePrintService::class.java)
        val adminToken = "Bearer ${jwt.generateAccessToken(7, Authority.ROLE_ADMIN).value}"
        val staffToken = "Bearer ${jwt.generateStaffToken(StaffPrincipal(7, "expo-a")).value}"

        given("최고 관리자가 아닌 승인된 관리자") {
            `when`("관리자 승인 관리 경로를 호출하면") {
                then("목록 승인 거절 모두 403이다") {
                    mvc.perform(get("/admin").header("Authorization", adminToken)).andExpect(status().isForbidden)
                    mvc.perform(patch("/admin/8").header("Authorization", adminToken)).andExpect(status().isForbidden)
                    mvc.perform(delete("/admin/8").header("Authorization", adminToken)).andExpect(status().isForbidden)
                }
            }
        }
        given("DB에서 최고 관리자로 지정한 관리자") {
            `when`("승인 경로를 호출하면") {
                then("ROLE_ADMIN 토큰으로 승인할 수 있다") {
                    val token = "Bearer ${jwt.generateAccessToken(8, Authority.ROLE_ADMIN).value}"
                    mvc.perform(patch("/admin/9").header("Authorization", token)).andExpect(status().isNoContent)
                }
            }
        }
        given("다른 박람회에 권한이 없는 관리자") {
            every { client.requireAdminAccess("expo-b", adminToken, null) } throws forbidden()
            `when`("명단을 조회하면") {
                then("참가자와 연수자 모두 403이다") {
                    mvc.perform(get("/participant/expo-b").header("Authorization", adminToken)).andExpect(status().isForbidden)
                    mvc.perform(get("/trainee/expo-b").header("Authorization", adminToken)).andExpect(status().isForbidden)
                }
            }
        }
        given("관리자와 같은 숫자 inviteId의 스태프 토큰") {
            `when`("관리자 명단을 조회하면") {
                then("관리자로 인증되지 않고 403이다") {
                    jwt.verifyAdminId(staffToken.removePrefix("Bearer ")) shouldBe null
                    jwt.verifyStaff(staffToken.removePrefix("Bearer ")) shouldBe StaffPrincipal(7, "expo-a")
                    mvc.perform(get("/participant/expo-a").header("Authorization", staffToken)).andExpect(status().isForbidden)
                    mvc.perform(get("/trainee/expo-a").header("Authorization", staffToken)).andExpect(status().isForbidden)
                }
            }
            `when`("같은 박람회 명찰 작업을 처리하면") {
                then("DB 관리자 조회 없이 CHECKIN을 거쳐 inviteId를 기록한다") {
                    io.mockk.clearMocks(admins, answers = false)
                    mvc
                        .perform(
                            post("/admin/badge-prints/expo-a/jobs/1/resolve")
                                .header("Authorization", staffToken)
                                .contentType("application/json")
                                .content("""{"action":"DONE","requestKey":"00000000-0000-0000-0000-000000000001"}"""),
                        ).andExpect(status().isNoContent)
                    verify(exactly = 0) { admins.findById(any()) }
                    verify { client.requireAdminAccess("expo-a", staffToken, "CHECKIN") }
                    verify { badges.resolve("expo-a", 1, 7, any<ResolveBadgePrintJobReqDto>()) }
                }
            }
            `when`("다른 박람회 명찰을 조회하면") {
                then("403이다") {
                    mvc
                        .perform(
                            get("/admin/badge-prints/expo-b/stations").header("Authorization", staffToken),
                        ).andExpect(status().isForbidden)
                }
            }
            `when`("Expo가 회수된 링크를 거절하면") {
                then("이미 발급된 토큰도 403이다") {
                    every { client.requireAdminAccess("expo-a", staffToken, "CHECKIN") } throws forbidden()
                    mvc
                        .perform(
                            get("/admin/badge-prints/expo-a/stations").header("Authorization", staffToken),
                        ).andExpect(status().isForbidden)
                }
            }
        }
        given("유효한 링크 비밀값") {
            every { client.exchangeStaffInvite(any()) } returns StaffInviteExchangeResDto(7, "expo-a", Instant.now().plusSeconds(3600))
            `when`("공개 발급 API를 호출하면") {
                then("refresh token 없이 15분 스태프 JWT를 받는다") {
                    val result =
                        mvc
                            .perform(post("/staff-auth").contentType("application/json").content("""{"secret":"synthetic-invite"}"""))
                            .andExpect(status().isOk)
                            .andReturn()
                    val body = jacksonObjectMapper().readTree(result.response.contentAsString)
                    jwt.verifyStaff(body["accessToken"].asString()) shouldBe StaffPrincipal(7, "expo-a")
                    val claims = jacksonObjectMapper().readTree(Base64.getUrlDecoder().decode(body["accessToken"].asString().split('.')[1]))
                    claims["sub"].asString() shouldBe "staff:7"
                    claims["role"].asString() shouldBe "ROLE_STAFF"
                    claims["exp"].asLong() - claims["iat"].asLong() shouldBe 900L
                    body.has("refreshToken") shouldBe false
                }
            }
        }
        given("만료되거나 회수된 링크") {
            `when`("발급을 요청하면") {
                then("새 토큰을 발급하지 않는다") {
                    every { client.exchangeStaffInvite(any()) } returns StaffInviteExchangeResDto(7, "expo-a", Instant.EPOCH)
                    mvc
                        .perform(post("/staff-auth").contentType("application/json").content("""{"secret":"expired-fixture"}"""))
                        .andExpect(status().isUnauthorized)
                    every { client.exchangeStaffInvite(any()) } throws forbidden()
                    mvc
                        .perform(post("/staff-auth").contentType("application/json").content("""{"secret":"revoked-fixture"}"""))
                        .andExpect(status().isForbidden)
                }
            }
        }
        given("Expo 연결 실패") {
            `when`("명단 조회 권한을 확인하면") {
                then("허용하지 않고 503이다") {
                    every { client.requireAdminAccess("expo-c", adminToken, null) } throws IllegalStateException("offline")
                    mvc.perform(get("/trainee/expo-c").header("Authorization", adminToken)).andExpect(status().isServiceUnavailable)
                }
            }
        }
    })

private fun forbidden(): FeignException =
    FeignException.errorStatus(
        "admin-access",
        Response
            .builder()
            .status(403)
            .reason("Forbidden")
            .headers(emptyMap())
            .request(Request.create(Request.HttpMethod.GET, "http://expo.test", emptyMap(), null, Charsets.UTF_8, null))
            .build(),
    )

@TestConfiguration
@EnableWebMvc
@Import(SecurityConfig::class, ExpoAccessConfiguration::class, GlobalExceptionHandler::class)
class StaffSecurityTestConfig {
    @Bean fun objectMapper(): ObjectMapper = jacksonObjectMapper()

    @Bean fun internalProperties() = InternalProperties("synthetic-internal-token-0123456789")

    @Bean fun adminRepository(): AdminRepository =
        mockk<AdminRepository>().apply {
            every { findById(7) } returns
                Optional.of(
                    Admin(
                        id = 7,
                        name = "테스트",
                        nickname = "test",
                        email = "test@example.invalid",
                        password = "unused",
                        phoneNumber = "00000000000",
                        authority = Authority.ROLE_ADMIN,
                        status = Status.ACCEPTED,
                    ),
                )
            every { findById(8) } returns
                Optional.of(
                    Admin(
                        id = 8,
                        name = "테스트",
                        nickname = "super",
                        email = "super@example.invalid",
                        password = "unused",
                        phoneNumber = "00000000001",
                        authority = Authority.ROLE_ADMIN,
                        status = Status.ACCEPTED,
                        superAdmin = true,
                    ),
                )
        }

    @Bean fun jwtProvider(): JwtProvider {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pem = "-----BEGIN PRIVATE KEY-----\n${Base64.getEncoder().encodeToString(pair.private.encoded)}\n-----END PRIVATE KEY-----"
        return JwtProvider(JwtProperties(pem))
    }

    @Bean fun expoClient(): ExpoClient = mockk(relaxed = true)

    @Bean fun authorization(client: ExpoClient) = ExpoAuthorization(client)

    @Bean fun badgePrintService(): BadgePrintService = mockk(relaxed = true)

    @Bean fun badges(service: BadgePrintService) = BadgePrintController(service)

    @Bean fun participants() = ParticipantController(mockk(relaxed = true))

    @Bean fun trainees() = TraineeController(mockk(relaxed = true))

    @Bean fun admins() =
        AdminController(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))

    @Bean fun staffAuth(
        authorization: ExpoAuthorization,
        jwt: JwtProvider,
    ) = StaffAuthController(StaffAuthServiceImpl(authorization, jwt))
}
