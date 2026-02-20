package com.quantjumpstock.core.infrastructure.util

object DateRangeFormatter {

    fun format(startDate: String?, endDate: String?): String {
        return when {
            startDate != null && endDate != null -> "$startDate ~ $endDate"
            startDate != null -> "$startDate ~ 오늘"
            else -> "자동 (마지막 수집일+1 ~ 오늘)"
        }
    }
}
