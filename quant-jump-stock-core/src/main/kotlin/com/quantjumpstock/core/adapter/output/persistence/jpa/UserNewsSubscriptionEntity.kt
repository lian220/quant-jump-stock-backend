package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(
    name = "user_news_subscriptions",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["user_id", "subscription_type", "subscription_value", "notify_channel"])
    ]
)
class UserNewsSubscriptionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "subscription_type", nullable = false, length = 20)
    val subscriptionType: String,

    @Column(name = "subscription_value", nullable = false, length = 100)
    val subscriptionValue: String,

    @Column(name = "display_name", length = 200)
    val displayName: String? = null,

    @Column(name = "notify_channel", nullable = false, length = 20)
    val notifyChannel: String = "IN_APP",

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
