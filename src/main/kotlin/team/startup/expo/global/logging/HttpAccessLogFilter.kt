package team.startup.expo.global.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.DispatcherServlet
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.util.DisconnectedClientHelper

/**
 * 요청마다 접근 로그를 한 줄 남긴다(`PATCH /admin/{admin_id} status: 200 (12ms)`). 모든 Expo 서비스가 같은 형식을 쓴다.
 *
 * 경로는 매칭된 라우트 템플릿을 쓰고, 템플릿이 없는 요청(보안 필터에서 막힌 요청, 없는 경로)만 쿼리 없는 원본 URI를 200자까지 쓴다.
 * 쿼리, 헤더, 쿠키, 본문은 남기지 않는다(전화번호·세션이 실린다). 전화번호는 URL에 두지 않는다는 규칙(AGENTS.md "Auth")에 기댄다.
 * 보안 필터보다 먼저 등록되어 401/403도 남긴다. 클라이언트가 끊은 요청은 499 ERROR로 남긴다.
 */
class HttpAccessLogFilter(
    private val log: Logger = LoggerFactory.getLogger(LOGGER_NAME),
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI.removePrefix(request.contextPath)
        return SKIPPED_PATH.matches(path)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val startedAt = System.nanoTime()
        var failure: Throwable? = null
        try {
            filterChain.doFilter(request, response)
        } catch (exception: Throwable) {
            failure = exception
            throw exception
        } finally {
            write(request, response, failure, (System.nanoTime() - startedAt) / NANOS_PER_MILLI)
        }
    }

    private fun write(
        request: HttpServletRequest,
        response: HttpServletResponse,
        failure: Throwable?,
        elapsedMs: Long,
    ) {
        val handled = request.getAttribute(DispatcherServlet.EXCEPTION_ATTRIBUTE) as? Throwable
        val status =
            when {
                DisconnectedClientHelper.isClientDisconnectedException(failure ?: handled) -> CLIENT_CLOSED_REQUEST
                failure != null -> HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                else -> response.status
            }
        val message = "${request.method} ${pathOf(request)} status: $status (${elapsedMs}ms)"
        when {
            status == CLIENT_CLOSED_REQUEST || status >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR -> log.error(message)
            status >= HttpServletResponse.SC_BAD_REQUEST -> log.warn(message)
            else -> log.info(message)
        }
    }

    private fun pathOf(request: HttpServletRequest): String =
        request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)?.toString()
            ?: request.requestURI.take(MAX_RAW_PATH_LENGTH)

    companion object {
        /** 로거 이름이 곧 JSON `module` 값이 된다(`ModuleNameConverter`: 점이 없는 이름은 그대로 쓴다). */
        const val LOGGER_NAME = "HttpAccess"
        const val CLIENT_CLOSED_REQUEST = 499
        private const val MAX_RAW_PATH_LENGTH = 200
        private const val NANOS_PER_MILLI = 1_000_000L
        private val SKIPPED_PATH = Regex("""^(/actuator(/.*)?|(/auth)?/v3/api-docs(/.*)?|(/auth)?/swagger-ui(\.html|/.*)?)$""")
    }
}
