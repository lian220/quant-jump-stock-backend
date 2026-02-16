package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "news_categories")
class NewsCategoryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true, length = 100)
    val name: String,

    @Column(name = "name_en", nullable = false, length = 100)
    val nameEn: String,

    @Column(name = "category_group", nullable = false, length = 30)
    val categoryGroup: String,

    @Column(length = 300)
    val description: String? = null,

    @Column(length = 50)
    val icon: String? = null,

    @Column(nullable = false)
    val weight: Double = 0.10,

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,

    @Column(name = "sort_order", nullable = false)
    val sortOrder: Int = 0,

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
