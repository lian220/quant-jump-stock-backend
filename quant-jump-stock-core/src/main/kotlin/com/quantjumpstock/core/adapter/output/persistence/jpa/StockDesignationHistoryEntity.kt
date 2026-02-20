package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "stock_designation_history")
class StockDesignationHistoryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "stock_id", nullable = false)
    val stockId: Long,

    @Column(name = "previous_status", nullable = false, length = 20)
    val previousStatus: String,

    @Column(name = "new_status", nullable = false, length = 20)
    val newStatus: String,

    @Column(columnDefinition = "TEXT")
    val reason: String? = null,

    @Column(name = "changed_by")
    val changedBy: Long? = null,

    @Column(name = "changed_at", nullable = false)
    val changedAt: LocalDateTime = LocalDateTime.now()
)
