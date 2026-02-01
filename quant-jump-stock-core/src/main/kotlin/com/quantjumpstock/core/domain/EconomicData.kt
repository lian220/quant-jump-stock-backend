package com.quantjumpstock.core.domain

import java.time.LocalDateTime
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field

@Document(collection = "economic_data")
data class EconomicData(
        @Id val id: String? = null,
        val date: LocalDateTime,
        val indicators: Map<String, Double?> = emptyMap(),
        @Field("created_at") val createdAt: LocalDateTime = LocalDateTime.now()
)
