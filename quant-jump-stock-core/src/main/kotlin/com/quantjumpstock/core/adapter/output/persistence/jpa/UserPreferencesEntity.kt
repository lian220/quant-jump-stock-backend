package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "user_preferences")
class UserPreferencesEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", unique = true, nullable = false)
    val userId: Long,

    @Column(name = "investment_categories", length = 500)
    val investmentCategories: String? = null,

    @Column(name = "markets", length = 100)
    val markets: String? = null,

    @Column(name = "risk_tolerance", length = 20)
    val riskTolerance: String? = null,

    @Column(name = "onboarding_completed")
    val onboardingCompleted: Boolean = false,

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
