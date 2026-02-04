package com.quantjumpstock.core.application.marketplace

import com.quantjumpstock.core.adapter.output.persistence.jpa.BacktestStatus
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyJpaRepository
import com.quantjumpstock.core.application.strategy.CategoryInfo
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Marketplace 서비스
 * 공개 전략 목록 조회 및 관리
 */
@Service
@Transactional(readOnly = true)
class MarketplaceService(
    private val strategyRepository: StrategyJpaRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 공개 전략 목록 조회
     * 필터링 및 정렬 지원
     */
    fun getPublicStrategies(request: StrategyListRequest): StrategyListResponse {
        logger.info("공개 전략 목록 조회: categoryCode=${request.categoryCode}, minCagr=${request.minCagr}, maxMdd=${request.maxMdd}, sortBy=${request.sortBy}")

        val page = when (request.sortBy?.lowercase()) {
            "cagr" -> strategyRepository.findMarketplaceStrategiesByCagr(
                request.categoryCode,
                request.minCagr,
                request.maxMdd,
                request.toPageable()
            )
            "sharpe" -> strategyRepository.findMarketplaceStrategiesBySharpe(
                request.categoryCode,
                request.minCagr,
                request.maxMdd,
                request.toPageable()
            )
            else -> strategyRepository.findMarketplaceStrategies(
                request.categoryCode,
                request.minCagr,
                request.maxMdd,
                request.toPageable()
            )
        }

        val strategies = page.content.map { it.toDto() }
        val pagination = page.toPaginationDto()

        logger.info("공개 전략 목록 조회 완료: 총 ${pagination.totalElements}개, 현재 페이지 ${pagination.currentPage + 1}/${pagination.totalPages}")

        return StrategyListResponse(
            strategies = strategies,
            pagination = pagination
        )
    }

    /**
     * StrategyEntity를 StrategyDto로 변환
     */
    private fun StrategyEntity.toDto(): StrategyDto {
        val latestBacktest = this.backtestResults
            .filter { it.status == BacktestStatus.COMPLETED }
            .maxByOrNull { it.createdAt }

        return StrategyDto(
            id = this.id!!,
            name = this.name,
            description = this.description,
            category = CategoryInfo(
                id = this.category.id!!,
                code = this.category.code,
                name = this.category.name
            ),
            isPremium = this.isPremium,
            subscriberCount = this.subscriberCount,
            averageRating = this.averageRating,
            rebalanceFrequency = this.rebalanceFrequency.name,
            backtestResult = latestBacktest?.let {
                BacktestResultDto(
                    cagr = it.cagr,
                    mdd = it.mdd,
                    sharpeRatio = it.sharpeRatio,
                    totalReturn = it.totalReturn,
                    volatility = it.volatility,
                    winRate = it.winRate,
                    startDate = it.startDate.toString(),
                    endDate = it.endDate.toString()
                )
            },
            createdAt = this.createdAt
        )
    }

    /**
     * Page를 PaginationDto로 변환
     */
    private fun Page<*>.toPaginationDto(): PaginationDto {
        return PaginationDto(
            currentPage = this.number,
            pageSize = this.size,
            totalElements = this.totalElements,
            totalPages = this.totalPages,
            isFirst = this.isFirst,
            isLast = this.isLast
        )
    }
}
