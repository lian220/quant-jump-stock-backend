package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.marketplace.MarketplaceStrategy
import com.quantjumpstock.core.domain.model.marketplace.StrategyDetail
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.math.BigDecimal

/**
 * Marketplace Repository Port
 * 마켓플레이스 전략 조회를 위한 도메인 포트
 */
interface MarketplaceRepository {

    /**
     * 마켓플레이스 전략 조회 (기본 정렬: 구독자 수)
     */
    fun findMarketplaceStrategies(
        categoryCode: String?,
        minCagr: BigDecimal?,
        maxMdd: BigDecimal?,
        pageable: Pageable
    ): Page<MarketplaceStrategy>

    /**
     * 마켓플레이스 전략 조회 (CAGR 정렬)
     */
    fun findMarketplaceStrategiesByCagr(
        categoryCode: String?,
        minCagr: BigDecimal?,
        maxMdd: BigDecimal?,
        pageable: Pageable
    ): Page<MarketplaceStrategy>

    /**
     * 마켓플레이스 전략 조회 (Sharpe Ratio 정렬)
     */
    fun findMarketplaceStrategiesBySharpe(
        categoryCode: String?,
        minCagr: BigDecimal?,
        maxMdd: BigDecimal?,
        pageable: Pageable
    ): Page<MarketplaceStrategy>

    /**
     * 전략 상세 조회 (공개된 활성 전략만)
     * 성과 지표, 수익 곡선, 현재 보유 종목 포함
     */
    fun findPublicStrategyById(id: Long): StrategyDetail?
}
