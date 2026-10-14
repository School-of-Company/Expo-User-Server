package team.startup.expo.global.exception

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(ExpectedException::class)
    fun handleExpectedException(exception: ExpectedException): ResponseEntity<ErrorResponse> {
        val response = ResponseEntity.status(exception.status)
        exception.retryAfterSeconds?.let { response.header(HttpHeaders.RETRY_AFTER, it.toString()) }
        return response.body(ErrorResponse(status = exception.status.value(), message = exception.message))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unhandled API exception", exception)
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.")
    }

    // 검증에 실패한 필드와 사유를 함께 내려, 클라이언트가 어느 입력이 왜 틀렸는지 보여줄 수 있게 한다
    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val errors =
            ex.bindingResult.fieldErrors
                .groupBy({ it.field }, { it.defaultMessage ?: "올바르지 않은 값입니다." })
                .mapValues { (_, messages) -> messages.first() }
        return ResponseEntity
            .status(status)
            .headers(headers)
            .body(ErrorResponse(status = status.value(), message = "잘못된 요청입니다.", errors = errors))
    }

    // Spring MVC 기본 예외(400, 404, 405, 415 등)는 원래 상태 코드를 유지하고 응답 형식만 맞춘다
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val message = if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) "잘못된 요청입니다." else "요청을 처리할 수 없습니다."
        return ResponseEntity
            .status(statusCode)
            .headers(headers)
            .body(ErrorResponse(status = statusCode.value(), message = message))
    }

    private fun errorResponse(
        status: HttpStatus,
        message: String,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(status)
            .body(ErrorResponse(status = status.value(), message = message))
}
