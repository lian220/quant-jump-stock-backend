package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

@Entity
@Table(
    name = "news_source_tag_mappings",
    uniqueConstraints = [UniqueConstraint(columnNames = ["source", "source_tag"])]
)
class NewsSourceTagMappingEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 50)
    val source: String,

    @Column(name = "source_tag", nullable = false, length = 200)
    val sourceTag: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    val category: NewsCategoryEntity,

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
