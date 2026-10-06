package team.startup.expo.support

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import team.startup.expo.domain.user.entity.Authority
import team.startup.expo.global.security.jwt.JwtProvider
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.Base64

/**
 * 실제 애플리케이션 컨텍스트를 PostgreSQL과 Redis 컨테이너 위에서 띄운다. 같은 설정을 쓰는 테스트 클래스는
 * 컨텍스트와 컨테이너를 공유한다.
 */
@SpringBootTest(properties = ["eureka.client.enabled=false"])
@AutoConfigureMockMvc
@Import(IntegrationTestSupport.ContainersConfig::class)
abstract class IntegrationTestSupport {
    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    protected lateinit var jwtProvider: JwtProvider

    /** 서비스가 서명한 access token의 `Authorization` 헤더 값. 존재하지 않는 id여도 토큰은 만들어진다. */
    protected fun bearerOf(adminId: Long): String = "Bearer ${jwtProvider.generateAccessToken(adminId, Authority.ROLE_ADMIN).value}"

    protected fun clearAdmins() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_admin RESTART IDENTITY CASCADE")
    }

    @TestConfiguration(proxyBeanMethods = false)
    class ContainersConfig {
        @Bean
        @ServiceConnection
        fun postgres(): PostgreSQLContainer = PostgreSQLContainer("postgres:17-alpine")

        @Bean
        @ServiceConnection(name = "redis")
        fun redis(): GenericContainer<*> = GenericContainer("redis:8-alpine").withExposedPorts(REDIS_PORT)
    }

    companion object {
        const val REDIS_PORT = 6379

        const val INTERNAL_TOKEN = "test-internal-token-0123456789abcdef"

        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("jwt.private-key") { generatePrivateKeyPem() }
            registry.add("internal.token") { INTERNAL_TOKEN }
        }

        /** 서비스가 서명한 토큰을 테스트에서 검증할 수 있도록 키쌍을 한 번만 만들어 공유한다. */
        val testKeyPair: KeyPair by lazy {
            KeyPairGenerator
                .getInstance("RSA")
                .apply { initialize(2048) }
                .generateKeyPair()
        }

        fun generatePrivateKeyPem(): String {
            val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(testKeyPair.private.encoded)
            return "-----BEGIN PRIVATE KEY-----\n$body\n-----END PRIVATE KEY-----"
        }
    }
}
