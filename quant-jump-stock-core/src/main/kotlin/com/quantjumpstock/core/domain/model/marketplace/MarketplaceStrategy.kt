package com.quantjumpstock.core.domain.model.marketplace

import com.quantjumpstock.core.domain.model.backtest.BacktestSummary
import com.quantjumpstock.core.domain.model.strategy.RebalanceFrequency
import com.quantjumpstock.core.domain.model.strategy.StockSelectionType
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 마켓플레이스 전략 조회용 도메인 모델
 * 전략 정보와 최신 백테스트 결과를 포함
 */
data class MarketplaceStrategy(
    val id: Long,
    val name: String,
    val description: String?,
    val categoryId: Long,
    val categoryCode: String,
    val categoryName: String,
    val isPremium: Boolean,
    val stockSelectionType: StockSelectionType,
    val subscriberCount: Int,
    val averageRating: BigDecimal,
    val rebalanceFrequency: RebalanceFrequency,
    val latestBacktest: BacktestSummary?,
    val createdAt: LocalDateTime,
    // SCRUM-344: 유니버스 설정
    val recommendedUniverseType: String = "MARKET",
    val supportedUniverseTypes: List<String> = listOf("MARKET", "PORTFOLIO", "FIXED")
)
