package com.quantjumpstock.core.infrastructure.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.Authentication

/**
 * Security 관련 유틸리티
 */
object SecurityUtils {

    /**
     * 현재 로그인한 사용자 정보 조회
     * @return UserPrincipal 또는 null
     */
    fun getCurrentUser(): UserPrincipal? {
        val authentication: Authentication? = SecurityContextHolder.getContext().authentication
        return when {
            authentication == null -> null
            authentication.principal is UserPrincipal -> authentication.principal as UserPrincipal
            else -> null
        }
    }

    /**
     * 현재 로그인한 사용자 ID 조회
     * @return userId 또는 null
     */
    fun getCurrentUserId(): String? {
        return getCurrentUser()?.userId
    }

    /**
     * 현재 로그인한 사용자인지 확인
     * @param userId 확인할 사용자 ID
     * @return true: 본인, false: 타인 또는 미인증
     */
    fun isCurrentUser(userId: String): Boolean {
        val currentUserId = getCurrentUserId() ?: return false
        return currentUserId == userId
    }

    /**
     * 관리자 권한 확인
     * @return true: 관리자, false: 일반 사용자
     */
    fun isAdmin(): Boolean {
        val user = getCurrentUser() ?: return false
        return user.roles.contains("ADMIN")
    }
}
