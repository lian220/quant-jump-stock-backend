package com.quantjumpstock.core.infrastructure.security

/**
 * 접근 권한이 없을 때 발생하는 예외
 */
class AccessDeniedException(
    message: String = "Access denied. You can only access your own resources."
) : RuntimeException(message)

/**
 * 인증되지 않은 사용자가 접근할 때 발생하는 예외
 */
class UnauthorizedException(
    message: String = "Authentication required. Please login first."
) : RuntimeException(message)
