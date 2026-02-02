package com.quantjumpstock.core.adapter.input

import com.quantjumpstock.core.application.auth.AuthException
import com.quantjumpstock.core.infrastructure.security.AccessDeniedException
import com.quantjumpstock.core.infrastructure.security.UnauthorizedException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 전역 예외 처리 핸들러
 */
@RestControllerAdvice
class GlobalExceptionHandler {

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
     * 모든 예외 처리 (최종 fallback)
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("❌ Unexpected error occurred", ex)

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
