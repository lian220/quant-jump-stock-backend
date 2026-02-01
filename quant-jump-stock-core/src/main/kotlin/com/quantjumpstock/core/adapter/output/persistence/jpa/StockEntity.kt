package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import java.time.LocalDateTime
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp

@Entity
@Table(name = "stocks")
data class StockEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(unique = true, nullable = false, length = 20)
    val ticker: String,

    @Column(name = "stock_name", nullable = false, length = 200)
    val stockName: String,

    @Column(name = "stock_name_en", length = 200)
    val stockNameEn: String? = null,

    @Column(name = "is_etf", nullable = false)
    val isEtf: Boolean = false,

    @Column(name = "leverage_ticker", length = 20)
    val leverageTicker: String? = null,

    @Column(length = 50)
    val exchange: String? = null,

    @Column(length = 100)
    val sector: String? = null,

    @Column(length = 100)
    val industry: String? = null,

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
