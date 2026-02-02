package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import java.time.LocalDateTime
import org.hibernate.annotations.CreationTimestamp

@Entity
@Table(name = "permissions")
data class PermissionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(unique = true, nullable = false, length = 100)
    val code: String,

    @Column(nullable = false, length = 100)
    val name: String,

    @Column(length = 255)
    val description: String? = null,

    @Column(length = 50)
    val category: String? = null,

    @OneToMany(mappedBy = "permission", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val rolePermissions: MutableList<RolePermissionEntity> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        // User Management
        const val USER_VIEW = "USER_VIEW"
        const val USER_CREATE = "USER_CREATE"
        const val USER_UPDATE = "USER_UPDATE"
        const val USER_DELETE = "USER_DELETE"
        const val USER_ROLE_ASSIGN = "USER_ROLE_ASSIGN"

        // Strategy Management
        const val STRATEGY_VIEW = "STRATEGY_VIEW"
        const val STRATEGY_CREATE = "STRATEGY_CREATE"
        const val STRATEGY_UPDATE = "STRATEGY_UPDATE"
        const val STRATEGY_DELETE = "STRATEGY_DELETE"
        const val STRATEGY_APPROVE = "STRATEGY_APPROVE"
        const val STRATEGY_PUBLISH = "STRATEGY_PUBLISH"

        // Backtest
        const val BACKTEST_RUN = "BACKTEST_RUN"
        const val BACKTEST_VIEW_ALL = "BACKTEST_VIEW_ALL"
        const val BACKTEST_UNLIMITED = "BACKTEST_UNLIMITED"

        // Analytics
        const val ANALYTICS_VIEW = "ANALYTICS_VIEW"
        const val ANALYTICS_EXPORT = "ANALYTICS_EXPORT"

        // System
        const val SYSTEM_SETTINGS = "SYSTEM_SETTINGS"
        const val SYSTEM_LOGS = "SYSTEM_LOGS"
        const val SYSTEM_MAINTENANCE = "SYSTEM_MAINTENANCE"

        // Subscription & Payment
        const val SUBSCRIPTION_VIEW = "SUBSCRIPTION_VIEW"
        const val SUBSCRIPTION_MANAGE = "SUBSCRIPTION_MANAGE"
        const val PAYMENT_VIEW = "PAYMENT_VIEW"
        const val PAYMENT_REFUND = "PAYMENT_REFUND"
    }
}
