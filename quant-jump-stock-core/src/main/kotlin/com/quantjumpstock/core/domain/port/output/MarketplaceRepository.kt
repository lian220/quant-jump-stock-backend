package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.marketplace.MarketplaceStrategy
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
}
