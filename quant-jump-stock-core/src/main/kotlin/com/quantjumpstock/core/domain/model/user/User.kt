package com.quantjumpstock.core.domain.model.user

import java.time.LocalDateTime

/**
 * User 도메인 모델 - 순수 Kotlin
 */
data class User(
    val id: Long? = null,
    val userId: String,
    val name: String? = null,
    val email: String? = null,
    val passwordHash: String? = null,
    val oauthProvider: OAuthProvider? = null,
    val oauthProviderId: String? = null,
    val profileImageUrl: String? = null,
    val status: UserStatus = UserStatus.ACTIVE,
    val role: UserRole = UserRole.USER,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        require(userId.isNotBlank()) { "User ID must not be blank" }
    }

    /**
     * 활성 상태 여부
     */
    fun isActive(): Boolean = status == UserStatus.ACTIVE

    /**
     * 관리자 여부
     */
    fun isAdmin(): Boolean = role == UserRole.ADMIN

    /**
     * 사용자 비활성화
     */
    fun deactivate(): User = copy(status = UserStatus.INACTIVE, updatedAt = LocalDateTime.now())

    /**
     * 사용자 정지
     */
    fun suspend(): User = copy(status = UserStatus.SUSPENDED, updatedAt = LocalDateTime.now())

    /**
     * 사용자 활성화
     */
    fun activate(): User = copy(status = UserStatus.ACTIVE, updatedAt = LocalDateTime.now())

    /**
     * OAuth 사용자 여부
     */
    fun isOAuthUser(): Boolean = oauthProvider != null

    /**
     * 비밀번호 기반 로그인 사용자 여부
     */
    fun hasPassword(): Boolean = passwordHash != null

    /**
     * OAuth 정보 연결
     */
    fun linkOAuth(provider: OAuthProvider, providerId: String, profileImage: String? = null): User {
        return copy(
            oauthProvider = provider,
            oauthProviderId = providerId,
            profileImageUrl = profileImage ?: this.profileImageUrl,
            updatedAt = LocalDateTime.now()
        )
    }
}
