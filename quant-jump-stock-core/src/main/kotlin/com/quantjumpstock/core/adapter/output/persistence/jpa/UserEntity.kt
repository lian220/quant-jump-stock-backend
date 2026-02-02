package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import java.time.LocalDateTime
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp

@Entity
@Table(name = "users")
data class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", unique = true, nullable = false, length = 50)
    val userId: String,

    @Column(length = 100)
    val name: String? = null,

    @Column(unique = true, length = 100)
    val email: String? = null,

    @Column(name = "password_hash", length = 255)
    val passwordHash: String? = null,

    // 휴대폰 번호 (V15)
    @Column(name = "phone_number", length = 20)
    val phoneNumber: String? = null,

    @Column(name = "phone_verified")
    val phoneVerified: Boolean = false,

    @Column(name = "email_verified")
    val emailVerified: Boolean = false,

    // OAuth 관련 필드 (V14)
    @Enumerated(EnumType.STRING)
    @Column(name = "oauth_provider", length = 20)
    val oauthProvider: OAuthProvider? = null,

    @Column(name = "oauth_provider_id", length = 255)
    val oauthProviderId: String? = null,

    @Column(name = "profile_image_url", length = 500)
    val profileImageUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    val status: UserStatus = UserStatus.ACTIVE,

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    val role: UserRole = UserRole.USER,

    @OneToOne(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var tradingConfig: TradingConfigEntity? = null,

    @OneToOne(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var accountBalance: AccountBalanceEntity? = null,

    @OneToOne(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var kisAccount: UserKisAccountEntity? = null,

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val trades: MutableList<TradeEntity> = mutableListOf(),

    // RBAC - 사용자 역할 (V13)
    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val userRoles: MutableList<UserRoleEntity> = mutableListOf(),

    // 사용자 티어 - FREE/PREMIUM (V12)
    @OneToOne(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var userTier: UserTierEntity? = null,

    // 소유한 전략 (V12)
    @OneToMany(mappedBy = "owner", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val ownedStrategies: MutableList<StrategyEntity> = mutableListOf(),

    // 구독 중인 전략 (V12)
    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val strategySubscriptions: MutableList<StrategySubscriptionEntity> = mutableListOf(),

    // 백테스트 결과 (V12)
    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val backtestResults: MutableList<BacktestResultEntity> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * RBAC 기반 권한 확인
     */
    fun hasPermission(permissionCode: String): Boolean {
        return userRoles.any { userRole ->
            userRole.role.rolePermissions.any { rp ->
                rp.permission.code == permissionCode
            }
        }
    }

    /**
     * 특정 역할 보유 여부
     */
    fun hasRole(roleName: String): Boolean {
        return userRoles.any { it.role.name == roleName }
    }

    /**
     * Admin 여부 (SUPER_ADMIN 또는 ADMIN)
     */
    fun isAdmin(): Boolean {
        return hasRole(RoleEntity.SUPER_ADMIN) || hasRole(RoleEntity.ADMIN)
    }

    /**
     * 프리미엄 사용자 여부
     */
    fun isPremium(): Boolean {
        return userTier?.isPremiumActive() == true
    }
}

enum class UserStatus {
    ACTIVE, INACTIVE, SUSPENDED
}

enum class UserRole {
    ADMIN, USER, MODERATOR
}

enum class OAuthProvider {
    GOOGLE, NAVER
}
