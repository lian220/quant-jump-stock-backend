package com.quantjumpstock.core.domain.model.stock

import java.time.LocalDateTime

/**
 * 종목 지정 상태 변경 이력 도메인 모델
 */
data class StockDesignationHistory(
    val id: Long? = null,
    val stockId: Long,
    val previousStatus: DesignationStatus,
    val newStatus: DesignationStatus,
    val reason: String? = null,
    val changedBy: Long? = null,
    val changedAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        require(previousStatus != newStatus) { "Previous and new status must be different" }
    }
}
