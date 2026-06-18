package com.quantjumpstock.core.domain.model.stock

import com.fasterxml.jackson.annotation.JsonCreator

enum class PriceHistoryPeriod(val token: String) {
    ONE_MONTH("1m"),
    THREE_MONTHS("3m"),
    SIX_MONTHS("6m"),
    ONE_YEAR("1y");

    companion object {
        @JsonCreator
        @JvmStatic
        fun from(value: String): PriceHistoryPeriod =
            entries.firstOrNull { it.token == value.lowercase() }
                ?: throw IllegalArgumentException("invalid period: $value")
    }
}
