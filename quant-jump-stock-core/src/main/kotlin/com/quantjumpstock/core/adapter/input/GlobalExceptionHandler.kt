package com.quantjumpstock.core.adapter.input

import com.quantjumpstock.core.adapter.output.notification.slack.SlackApiClient
import com.quantjumpstock.core.application.auth.AuthException
import com.quantjumpstock.core.application.marketplace.StrategyNotFoundException
import com.quantjumpstock.core.infrastructure.security.AccessDeniedException
import com.quantjumpstock.core.infrastructure.security.UnauthorizedException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * 전역 예외 처리 핸들러
 */
@RestControllerAdvice
class GlobalExceptionHandler(
    private val slackApiClient: SlackApiClient
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 인증 실패 예외 처리 (로그인 실패 등)
     */
    @ExceptionHandler(AuthException::class)
    fun handleAuthException(ex: AuthException): ResponseEntity<ErrorResponse> {
        logger.warn("🔐 Authentication failed: {}", ex.message)

        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(
                error = "Authentication Failed",
                message = ex.message ?: "Authentication failed",
                status = HttpStatus.UNAUTHORIZED.value()
            ))
    }

    /**
     * 인증 필요 예외 처리
     */
    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorizedException(ex: UnauthorizedException): ResponseEntity<ErrorResponse> {
        logger.warn("🔒 Unauthorized access attempt: {}", ex.message)

        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(
                error = "Unauthorized",
                message = ex.message ?: "Authentication required",
                status = HttpStatus.UNAUTHORIZED.value()
            ))
    }

    /**
     * 접근 권한 없음 예외 처리
     */
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(ex: AccessDeniedException): ResponseEntity<ErrorResponse> {
        logger.warn("🚫 Access denied: {}", ex.message)

        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(
                error = "Forbidden",
                message = ex.message ?: "Access denied",
                status = HttpStatus.FORBIDDEN.value()
            ))
    }

    /**
     * 필수 헤더 누락 예외 처리 (Authorization 등)
     */
    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingRequestHeaderException(ex: MissingRequestHeaderException): ResponseEntity<ErrorResponse> {
        logger.warn("🔒 Missing required header: {}", ex.headerName)

        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(
                error = "Unauthorized",
                message = "인증이 필요합니다",
                status = HttpStatus.UNAUTHORIZED.value()
            ))
    }

    /**
     * JSON 파싱 에러 처리
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        logger.warn("⚠️ Invalid request body: {}", ex.message)

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                error = "Bad Request",
                message = "잘못된 요청 형식입니다",
                status = HttpStatus.BAD_REQUEST.value()
            ))
    }

    /**
     * 전략 없음 예외 처리
     */
    @ExceptionHandler(StrategyNotFoundException::class)
    fun handleStrategyNotFoundException(ex: StrategyNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn("📊 Strategy not found: {}", ex.message)

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(
                error = "Not Found",
                message = ex.message ?: "Strategy not found",
                status = HttpStatus.NOT_FOUND.value()
            ))
    }

    /**
     * 리소스 없음 예외 처리 (종목, 전략 등)
     */
    @ExceptionHandler(NoSuchElementException::class)
    fun handleNoSuchElementException(ex: NoSuchElementException): ResponseEntity<ErrorResponse> {
        logger.warn("🔍 Resource not found: {}", ex.message)

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(
                error = "Not Found",
                message = ex.message ?: "Resource not found",
                status = HttpStatus.NOT_FOUND.value()
            ))
    }

    /**
     * 일반 예외 처리
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        logger.warn("⚠️ Invalid argument: {}", ex.message)

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                error = "Bad Request",
                message = ex.message ?: "Invalid request",
                status = HttpStatus.BAD_REQUEST.value()
            ))
    }

    /**
     * 존재하지 않는 경로 접근 (봇/스캐너 차단용 - Slack 알림 제외)
     */
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFoundException(ex: NoResourceFoundException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.debug("🤖 Scanner/bot blocked: {} {}", request.method, request.requestURI)

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(
                error = "Not Found",
                message = "Resource not found",
                status = HttpStatus.NOT_FOUND.value()
            ))
    }

    /**
     * 모든 예외 처리 (최종 fallback)
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.error("❌ Unexpected error occurred", ex)

        // Slack 에러 알림 전송 (비동기적으로 처리, 실패해도 응답에 영향 없음)
        try {
            slackApiClient.notifyApiError(
                errorType = ex.javaClass.simpleName,
                errorMessage = ex.message ?: "Unknown error",
                requestPath = "${request.method} ${request.requestURI}",
                stackTrace = ex.stackTrace.take(10).joinToString("\n") { it.toString() }
            )
        } catch (e: Exception) {
            logger.warn("Failed to send Slack error notification", e)
        }

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(
                error = "Internal Server Error",
                message = "An unexpected error occurred",
                status = HttpStatus.INTERNAL_SERVER_ERROR.value()
            ))
    }
}

/**
 * 에러 응답 모델
 */
data class ErrorResponse(
    val error: String,
    val message: String,
    val status: Int,
    val timestamp: Long = System.currentTimeMillis()
)
