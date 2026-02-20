package com.quantjumpstock.core.application.portfolio

import com.quantjumpstock.core.domain.model.portfolio.PortfolioStock
import com.quantjumpstock.core.domain.model.portfolio.UserPortfolio
import com.quantjumpstock.core.domain.port.output.PortfolioStockRepository
import com.quantjumpstock.core.domain.port.output.StrategyDefaultStockRepository
import com.quantjumpstock.core.domain.port.output.StockRepository
import com.quantjumpstock.core.domain.port.output.UserPortfolioRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional(readOnly = true)
class UserPortfolioService(
    private val userPortfolioRepository: UserPortfolioRepository,
    private val portfolioStockRepository: PortfolioStockRepository,
    private val defaultStockRepository: StrategyDefaultStockRepository,
    private val stockRepository: StockRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 내 포트폴리오 목록 조회
     * N+1 방지: 종목 수를 단일 GROUP BY 쿼리로 일괄 조회
     */
    fun getUserPortfolios(userId: Long): List<UserPortfolioResponse> {
        val portfolios = userPortfolioRepository.findByUserId(userId)
        if (portfolios.isEmpty()) return emptyList()

        val portfolioIds = portfolios.mapNotNull { it.id }
        val stockCountMap = portfolioStockRepository.countByPortfolioIdIn(portfolioIds)

        return portfolios.map { portfolio ->
            val portfolioId = portfolio.id ?: throw IllegalStateException("포트폴리오에 ID가 없습니다")
            portfolio.toResponse(stockCountMap[portfolioId] ?: 0)
        }
    }

    /**
     * 포트폴리오 종목 목록 조회
     */
    fun getPortfolioStocks(portfolioId: Long, userId: Long): PortfolioStockListResponse {
        validatePortfolioOwnership(portfolioId, userId)

        val stocks = portfolioStockRepository.findByPortfolioId(portfolioId)
        val stockMap = loadStockMap(stocks.map { it.stockId })
        val responses = stocks.map { it.toResponse(stockMap) }
        val totalWeight = responses.sumOf { it.targetWeight }

        return PortfolioStockListResponse(
            stocks = responses,
            totalWeight = totalWeight,
            total = responses.size
        )
    }

    /**
     * 포트폴리오 종목 추가
     */
    @Transactional
    fun addPortfolioStock(portfolioId: Long, request: AddPortfolioStockRequest, userId: Long): PortfolioStockResponse {
        logger.info("포트폴리오 종목 추가: portfolioId=$portfolioId, stockId=${request.stockId}")

        validatePortfolioOwnership(portfolioId, userId)

        if (!stockRepository.existsById(request.stockId)) {
            throw PortfolioException("종목을 찾을 수 없습니다: ${request.stockId}")
        }

        // 중복 체크
        portfolioStockRepository.findByPortfolioIdAndStockId(portfolioId, request.stockId)?.let {
            throw PortfolioException("이미 추가된 종목입니다: stockId=${request.stockId}")
        }

        val portfolioStock = PortfolioStock(
            portfolioId = portfolioId,
            stockId = request.stockId,
            targetWeight = request.targetWeight,
            memo = request.memo
        )

        val saved = portfolioStockRepository.save(portfolioStock)
        val stockMap = loadStockMap(listOf(saved.stockId))
        return saved.toResponse(stockMap)
    }

    /**
     * 포트폴리오 종목 비중 수정
     */
    @Transactional
    fun updatePortfolioStock(portfolioId: Long, stockId: Long, request: UpdatePortfolioStockRequest, userId: Long): PortfolioStockResponse {
        logger.info("포트폴리오 종목 수정: portfolioId=$portfolioId, stockId=$stockId")

        validatePortfolioOwnership(portfolioId, userId)

        val existing = portfolioStockRepository.findByPortfolioIdAndStockId(portfolioId, stockId)
            ?: throw PortfolioException("포트폴리오 종목을 찾을 수 없습니다: portfolioId=$portfolioId, stockId=$stockId")

        val updated = existing.copy(
            targetWeight = request.targetWeight ?: existing.targetWeight,
            memo = request.memo ?: existing.memo
        )

        val saved = portfolioStockRepository.save(updated)
        val stockMap = loadStockMap(listOf(saved.stockId))
        return saved.toResponse(stockMap)
    }

    /**
     * 포트폴리오 종목 삭제
     */
    @Transactional
    fun removePortfolioStock(portfolioId: Long, stockId: Long, userId: Long): PortfolioResponse {
        logger.info("포트폴리오 종목 삭제: portfolioId=$portfolioId, stockId=$stockId")

        validatePortfolioOwnership(portfolioId, userId)

        val existing = portfolioStockRepository.findByPortfolioIdAndStockId(portfolioId, stockId)
            ?: throw PortfolioException("포트폴리오 종목을 찾을 수 없습니다: portfolioId=$portfolioId, stockId=$stockId")

        val existingId = existing.id ?: throw IllegalStateException("삭제할 포트폴리오 종목에 ID가 없습니다: portfolioId=$portfolioId, stockId=$stockId")
        portfolioStockRepository.deleteById(existingId)

        return PortfolioResponse(success = true, message = "종목이 삭제되었습니다")
    }

    /**
     * 전략 구독 시 기본 종목을 사용자 포트폴리오로 복사
     */
    @Transactional
    fun createFromStrategy(userId: Long, strategyId: Long): UserPortfolioResponse {
        logger.info("전략 기반 포트폴리오 생성: userId=$userId, strategyId=$strategyId")

        // 이미 해당 전략으로 생성한 포트폴리오가 있는지 확인
        userPortfolioRepository.findByUserIdAndStrategyId(userId, strategyId)?.let {
            throw PortfolioException("이미 해당 전략의 포트폴리오가 존재합니다")
        }

        // 전략 기본 종목 조회
        val defaultStocks = defaultStockRepository.findByStrategyId(strategyId)

        // 포트폴리오 생성
        val portfolio = UserPortfolio(
            userId = userId,
            strategyId = strategyId,
            name = "전략 #$strategyId 포트폴리오"
        )
        val savedPortfolio = userPortfolioRepository.save(portfolio)

        // 기본 종목 복사
        val savedPortfolioId = savedPortfolio.id ?: throw IllegalStateException("포트폴리오 저장 후 ID 생성 실패: userId=$userId, strategyId=$strategyId")
        defaultStocks.forEach { ds ->
            val portfolioStock = PortfolioStock(
                portfolioId = savedPortfolioId,
                stockId = ds.stockId,
                targetWeight = ds.targetWeight,
                isFromStrategy = true,
                memo = ds.memo
            )
            portfolioStockRepository.save(portfolioStock)
        }

        logger.info("전략 기반 포트폴리오 생성 완료: portfolioId=${savedPortfolio.id}, stocks=${defaultStocks.size}")

        return savedPortfolio.toResponse(defaultStocks.size)
    }

    // ===== Helper Methods =====

    private fun validatePortfolioExists(portfolioId: Long) {
        if (!userPortfolioRepository.existsById(portfolioId)) {
            throw PortfolioException("포트폴리오를 찾을 수 없습니다: $portfolioId")
        }
    }

    private fun validatePortfolioOwnership(portfolioId: Long, userId: Long) {
        val portfolio = userPortfolioRepository.findById(portfolioId)
            ?: throw PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다: $portfolioId")
        if (portfolio.userId != userId) {
            throw PortfolioAccessDeniedException("포트폴리오에 대한 접근 권한이 없습니다")
        }
    }

    private fun UserPortfolio.toResponse(stockCount: Int = 0): UserPortfolioResponse {
        return UserPortfolioResponse(
            id = this.id ?: throw IllegalStateException("포트폴리오에 ID가 없습니다"),
            userId = this.userId,
            strategyId = this.strategyId,
            name = this.name,
            description = this.description,
            isDefault = this.isDefault,
            stockCount = stockCount,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }

    private fun loadStockMap(stockIds: List<Long>): Map<Long, com.quantjumpstock.core.domain.model.stock.Stock> {
        return stockRepository.findAllByIds(stockIds.distinct()).associateBy {
            it.id ?: throw IllegalStateException("종목에 ID가 없습니다: ticker=${it.ticker}")
        }
    }

    private fun PortfolioStock.toResponse(stockMap: Map<Long, com.quantjumpstock.core.domain.model.stock.Stock>): PortfolioStockResponse {
        val stock = stockMap[this.stockId]
        return PortfolioStockResponse(
            id = this.id ?: throw IllegalStateException("포트폴리오 종목에 ID가 없습니다: portfolioId=$portfolioId, stockId=$stockId"),
            portfolioId = this.portfolioId,
            stockId = this.stockId,
            ticker = stock?.ticker,
            stockName = stock?.stockName,
            targetWeight = this.targetWeight,
            isFromStrategy = this.isFromStrategy,
            memo = this.memo,
            addedAt = this.addedAt
        )
    }
}
