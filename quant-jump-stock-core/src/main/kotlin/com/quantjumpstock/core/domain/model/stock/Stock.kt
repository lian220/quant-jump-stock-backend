package com.quantjumpstock.core.domain.model.stock

import java.time.LocalDateTime

/**
 * 시장 구분
 */
enum class Market {
    US, KR, CRYPTO
}

/**
 * 종목 지정 상태
 */
enum class DesignationStatus {
    NORMAL,     // 정상
    CAUTION,    // 주의
    WARNING,    // 경고
    DANGER,     // 위험
    DELISTED    // 상장폐지
}

/**
 * Stock 도메인 모델 - 순수 Kotlin
 *
 * JPA/MongoDB 어노테이션 없음
 * 비즈니스 로직 포함
 */
data class Stock(
    val id: Long? = null,
    val ticker: String,
    val stockName: String,
    val stockNameEn: String? = null,
    val exchange: String? = null,
    val sector: String? = null,
    val industry: String? = null,
    val market: Market = Market.KR,
    val isEtf: Boolean = false,
    val leverageTicker: String? = null,
    val designationStatus: DesignationStatus = DesignationStatus.NORMAL,
    val designationReason: String? = null,
    val designatedAt: LocalDateTime? = null,
    val isActive: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        require(ticker.isNotBlank()) { "Ticker must not be blank" }
        require(ticker.length <= 20) { "Ticker must be 20 characters or less" }
        require(stockName.isNotBlank()) { "Stock name must not be blank" }
        require(stockName.length <= 200) { "Stock name must be 200 characters or less" }
    }

    /**
     * 지정 상태 변경
     */
    fun changeDesignation(newStatus: DesignationStatus, reason: String?): Stock {
        require(newStatus != designationStatus) { "New designation status must be different from current" }
        return copy(
            designationStatus = newStatus,
            designationReason = reason,
            designatedAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }

    /**
     * 비활성화
     */
    fun deactivate(): Stock {
        return copy(isActive = false, updatedAt = LocalDateTime.now())
    }

    /**
     * 활성화
     */
    fun activate(): Stock {
        return copy(isActive = true, updatedAt = LocalDateTime.now())
    }
}
