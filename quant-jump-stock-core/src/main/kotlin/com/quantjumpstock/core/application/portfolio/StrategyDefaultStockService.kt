package com.quantjumpstock.core.application.portfolio

import com.quantjumpstock.core.domain.model.portfolio.StrategyDefaultStock
import com.quantjumpstock.core.domain.port.output.StrategyDefaultStockRepository
import com.quantjumpstock.core.domain.port.output.StrategyRepository
import com.quantjumpstock.core.domain.port.output.StockRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional(readOnly = true)
class StrategyDefaultStockService(
    private val defaultStockRepository: StrategyDefaultStockRepository,
    @Qualifier("strategyPersistenceAdapterV2")
    private val strategyRepository: StrategyRepository,
    private val stockRepository: StockRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 전략 기본 종목 목록 조회
     */
    fun getDefaultStocks(strategyId: Long): DefaultStockListResponse {
        validateStrategyExists(strategyId)

        val stocks = defaultStockRepository.findByStrategyId(strategyId)
        val stockMap = loadStockMap(stocks.map { it.stockId })
        val responses = stocks.map { it.toResponse(stockMap) }
        val totalWeight = responses.sumOf { it.targetWeight }

        return DefaultStockListResponse(
            stocks = responses,
            totalWeight = totalWeight,
            total = responses.size
        )
    }

    /**
     * 기본 종목 추가
     */
    @Transactional
    fun addDefaultStock(strategyId: Long, request: AddDefaultStockRequest): DefaultStockResponse {
        logger.info("기본 종목 추가: strategyId=$strategyId, stockId=${request.stockId}")

        validateStrategyExists(strategyId)
        validateStockExists(request.stockId)
        validateTargetWeight(request.targetWeight)

        // 비중 합 검증 (기존 종목 + 새 종목)
        val existingStocks = defaultStockRepository.findByStrategyId(strategyId)
        val existingTotal = existingStocks.sumOf { it.targetWeight }
        if (existingTotal + request.targetWeight > BigDecimal(100)) {
            throw PortfolioException("비중 합계가 100%를 초과할 수 없습니다: 기존 ${existingTotal}% + 추가 ${request.targetWeight}% = ${existingTotal + request.targetWeight}%")
        }

        // 중복 체크
        defaultStockRepository.findByStrategyIdAndStockId(strategyId, request.stockId)?.let {
            throw PortfolioException("이미 등록된 기본 종목입니다: stockId=${request.stockId}")
        }

        val defaultStock = StrategyDefaultStock(
            strategyId = strategyId,
            stockId = request.stockId,
            targetWeight = request.targetWeight,
            memo = request.memo
        )

        val saved = defaultStockRepository.save(defaultStock)
        logger.info("기본 종목 추가 완료: id=${saved.id}")

        val stockMap = loadStockMap(listOf(saved.stockId))
        return saved.toResponse(stockMap)
    }

    /**
     * 기본 종목 비중 수정
     */
    @Transactional
    fun updateDefaultStock(strategyId: Long, stockId: Long, request: UpdateDefaultStockRequest): DefaultStockResponse {
        logger.info("기본 종목 수정: strategyId=$strategyId, stockId=$stockId")

        val existing = defaultStockRepository.findByStrategyIdAndStockId(strategyId, stockId)
            ?: throw PortfolioException("기본 종목을 찾을 수 없습니다: strategyId=$strategyId, stockId=$stockId")

        val newWeight = request.targetWeight ?: existing.targetWeight
        validateTargetWeight(newWeight)

        // 비중 합 검증 (변경 후 전체 합)
        if (request.targetWeight != null) {
            val otherStocks = defaultStockRepository.findByStrategyId(strategyId)
                .filter { it.stockId != stockId }
            val otherTotal = otherStocks.sumOf { it.targetWeight }
            if (otherTotal + newWeight > BigDecimal(100)) {
                throw PortfolioException("비중 합계가 100%를 초과할 수 없습니다: 기타 ${otherTotal}% + 변경 ${newWeight}% = ${otherTotal + newWeight}%")
            }
        }

        val updated = existing.copy(
            targetWeight = newWeight,
            memo = request.memo ?: existing.memo
        )

        val saved = defaultStockRepository.save(updated)
        val stockMap = loadStockMap(listOf(saved.stockId))
        return saved.toResponse(stockMap)
    }

    /**
     * 기본 종목 삭제
     */
    @Transactional
    fun removeDefaultStock(strategyId: Long, stockId: Long): PortfolioResponse {
        logger.info("기본 종목 삭제: strategyId=$strategyId, stockId=$stockId")

        val existing = defaultStockRepository.findByStrategyIdAndStockId(strategyId, stockId)
            ?: throw PortfolioException("기본 종목을 찾을 수 없습니다: strategyId=$strategyId, stockId=$stockId")

        val existingId = existing.id ?: throw IllegalStateException("삭제할 기본 종목에 ID가 없습니다: strategyId=$strategyId, stockId=$stockId")
        defaultStockRepository.deleteById(existingId)

        return PortfolioResponse(success = true, message = "기본 종목이 삭제되었습니다")
    }

    /**
     * 기본 종목 전체 교체
     */
    @Transactional
    fun replaceDefaultStocks(strategyId: Long, request: ReplaceDefaultStocksRequest): DefaultStockListResponse {
        logger.info("기본 종목 전체 교체: strategyId=$strategyId, count=${request.stocks.size}")

        validateStrategyExists(strategyId)

        // 개별 비중 검증
        request.stocks.forEach { item ->
            validateTargetWeight(item.targetWeight)
        }

        // 비중 합 검증
        val totalWeight = request.stocks.sumOf { it.targetWeight }
        if (totalWeight > BigDecimal(100)) {
            throw PortfolioException("비중 합계가 100%를 초과할 수 없습니다: $totalWeight%")
        }

        // 기존 종목 전체 삭제
        defaultStockRepository.deleteByStrategyId(strategyId)

        // 새 종목 저장
        val saved = request.stocks.map { item ->
            validateStockExists(item.stockId)
            val defaultStock = StrategyDefaultStock(
                strategyId = strategyId,
                stockId = item.stockId,
                targetWeight = item.targetWeight,
                memo = item.memo
            )
            defaultStockRepository.save(defaultStock)
        }

        val stockMap = loadStockMap(saved.map { it.stockId })
        val responses = saved.map { it.toResponse(stockMap) }

        return DefaultStockListResponse(
            stocks = responses,
            totalWeight = totalWeight,
            total = responses.size
        )
    }

    // ===== Helper Methods =====

    private fun validateStrategyExists(strategyId: Long) {
        if (!strategyRepository.existsById(strategyId)) {
            throw PortfolioException("전략을 찾을 수 없습니다: $strategyId")
        }
    }

    private fun validateStockExists(stockId: Long) {
        if (!stockRepository.existsById(stockId)) {
            throw PortfolioException("종목을 찾을 수 없습니다: $stockId")
        }
    }

    private fun validateTargetWeight(targetWeight: BigDecimal) {
        if (targetWeight <= BigDecimal.ZERO) {
            throw PortfolioException("목표 비중은 0보다 커야 합니다: $targetWeight%")
        }
        if (targetWeight > BigDecimal(100)) {
            throw PortfolioException("목표 비중은 100%를 초과할 수 없습니다: $targetWeight%")
        }
    }

    private fun loadStockMap(stockIds: List<Long>): Map<Long, com.quantjumpstock.core.domain.model.stock.Stock> {
        return stockRepository.findAllByIds(stockIds.distinct()).associateBy {
            it.id ?: throw IllegalStateException("종목에 ID가 없습니다: ticker=${it.ticker}")
        }
    }

    private fun StrategyDefaultStock.toResponse(stockMap: Map<Long, com.quantjumpstock.core.domain.model.stock.Stock>): DefaultStockResponse {
        val stock = stockMap[this.stockId]
        return DefaultStockResponse(
            id = this.id ?: 0L,
            strategyId = this.strategyId,
            stockId = this.stockId,
            ticker = stock?.ticker,
            stockName = stock?.stockName,
            stockNameEn = stock?.stockNameEn,
            market = stock?.market?.name,
            targetWeight = this.targetWeight,
            memo = this.memo,
            createdAt = this.createdAt
        )
    }
}
