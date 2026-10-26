package team.startup.expo.global.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotContain
import jakarta.servlet.FilterChain
import org.apache.catalina.connector.ClientAbortException
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.HandlerMapping

class HttpAccessLogFilterSpec :
    BehaviorSpec({
        val logger = LoggerFactory.getLogger(HttpAccessLogFilter.LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>()

        beforeTest {
            appender.list.clear()
            appender.start()
            logger.addAppender(appender)
        }
        afterTest { logger.detachAppender(appender) }

        fun run(
            request: MockHttpServletRequest,
            chain: FilterChain,
        ) = HttpAccessLogFilter().doFilter(request, MockHttpServletResponse(), chain)

        fun respond(
            status: Int,
            template: String? = null,
        ) = FilterChain { req, res ->
            template?.let { req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, it) }
            (res as MockHttpServletResponse).status = status
        }

        given("라우트에 매칭된 요청") {
            `when`("200으로 끝나면") {
                then("템플릿 경로와 상태로 INFO 한 줄을 남긴다") {
                    run(MockHttpServletRequest("PATCH", "/admin/42"), respond(200, "/admin/{admin_id}"))

                    appender.list shouldHaveSize 1
                    appender.list[0].level shouldBe Level.INFO
                    appender.list[0].formattedMessage shouldMatch Regex("""PATCH /admin/\{admin_id} status: 200 \(\d+ms\)""")
                    ModuleNameConverter.moduleOf(appender.list[0].loggerName) shouldBe "HttpAccess"
                }
            }
            `when`("4xx로 끝나면") {
                then("WARN으로 남긴다") {
                    run(MockHttpServletRequest("GET", "/admin"), respond(404, "/admin"))

                    appender.list.single().level shouldBe Level.WARN
                }
            }
            `when`("5xx로 끝나면") {
                then("ERROR로 남긴다") {
                    run(MockHttpServletRequest("GET", "/admin"), respond(503, "/admin"))

                    appender.list.single().level shouldBe Level.ERROR
                }
            }
            `when`("처리되지 않은 예외가 빠져나오면") {
                then("500 ERROR로 한 번 남기고 예외는 그대로 던진다") {
                    shouldThrow<IllegalStateException> {
                        run(MockHttpServletRequest("GET", "/admin"), { _, _ -> throw IllegalStateException("boom") })
                    }

                    appender.list.single().level shouldBe Level.ERROR
                    appender.list.single().formattedMessage shouldMatch Regex("""GET /admin status: 500 \(\d+ms\)""")
                }
            }
            `when`("클라이언트가 연결을 끊으면") {
                then("499 ERROR로 한 번 남긴다") {
                    shouldThrow<ClientAbortException> {
                        run(MockHttpServletRequest("GET", "/admin"), { _, _ -> throw ClientAbortException() })
                    }

                    appender.list.single().level shouldBe Level.ERROR
                    appender.list.single().formattedMessage shouldMatch Regex("""GET /admin status: 499 \(\d+ms\)""")
                }
            }
        }

        given("라우트에 매칭되지 않은 요청") {
            then("쿼리 없는 원본 URI를 남긴다") {
                val request =
                    MockHttpServletRequest("GET", "/participant-auth/session").apply {
                        queryString = "phone=01012345678"
                        addHeader("Cookie", "PARTICIPANT_SESSION=secret")
                    }
                run(request, respond(401))

                val message = appender.list.single().formattedMessage
                message shouldMatch Regex("""GET /participant-auth/session status: 401 \(\d+ms\)""")
                message shouldNotContain "01012345678"
                message shouldNotContain "secret"
            }
            then("긴 URI는 200자까지만 남긴다") {
                run(MockHttpServletRequest("GET", "/" + "a".repeat(500)), respond(404))

                appender.list.single().formattedMessage shouldMatch Regex("""GET /a{199} status: 404 \(\d+ms\)""")
            }
        }

        given("모니터링과 문서 경로") {
            then("남기지 않는다") {
                listOf(
                    "/actuator/health",
                    "/actuator/prometheus",
                    "/auth/v3/api-docs",
                    "/auth/swagger-ui/index.html",
                    "/auth/swagger-ui.html",
                ).forEach { run(MockHttpServletRequest("GET", it), respond(200)) }

                appender.list.shouldBeEmpty()
            }
        }

        given("logging.http-access.enabled") {
            val runner = WebApplicationContextRunner().withUserConfiguration(HttpAccessLogConfig::class.java)

            `when`("설정하지 않으면") {
                then("필터를 등록한다") {
                    runner.run { it.getBeansOfType(FilterRegistrationBean::class.java).keys shouldHaveSize 1 }
                }
            }
            `when`("false면") {
                then("필터를 등록하지 않는다") {
                    runner
                        .withPropertyValues("logging.http-access.enabled=false")
                        .run { it.getBeansOfType(FilterRegistrationBean::class.java).keys.shouldBeEmpty() }
                }
            }
        }
    })
