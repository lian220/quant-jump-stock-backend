package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "user_notification_preferences")
class NotificationPreferenceEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", nullable = false, unique = true)
    val userId: Long,

    @Column(name = "news_enabled", nullable = false)
    var newsEnabled: Boolean = true,

    @Column(name = "announcement_enabled", nullable = false)
    var announcementEnabled: Boolean = true,

    @Column(name = "trading_signal_enabled", nullable = false)
    var tradingSignalEnabled: Boolean = true,

    @Column(name = "backtest_enabled", nullable = false)
    var backtestEnabled: Boolean = true,

    @Column(name = "price_alert_enabled", nullable = false)
    var priceAlertEnabled: Boolean = true,

    @Column(name = "weekly_digest_enabled", nullable = false)
    var weeklyDigestEnabled: Boolean = true,

    @Column(name = "push_enabled", nullable = false)
    var pushEnabled: Boolean = true,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime? = null
)
