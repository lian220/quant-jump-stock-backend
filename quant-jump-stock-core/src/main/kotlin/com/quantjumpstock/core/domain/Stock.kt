package com.quantjumpstock.core.domain

import java.time.LocalDateTime
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field

@Document(collection = "stocks")
data class Stock(
        @Id val id: String? = null,
        val ticker: String,
        @Field("stock_name") val stockName: String,
        @Field("stock_name_en") val stockNameEn: String? = null,
        @Field("is_etf") val isEtf: Boolean = false,
        @Field("leverage_ticker") val leverageTicker: String? = null,
        val exchange: String? = null,
        val sector: String? = null,
        val industry: String? = null,
        @Field("is_active") val isActive: Boolean = true,
        @Field("created_at") val createdAt: LocalDateTime = LocalDateTime.now(),
        @Field("updated_at") val updatedAt: LocalDateTime = LocalDateTime.now()
)
