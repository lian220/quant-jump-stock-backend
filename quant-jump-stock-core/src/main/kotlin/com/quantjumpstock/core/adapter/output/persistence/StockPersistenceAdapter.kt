package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.jpa.StockDesignationHistoryEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.StockDesignationHistoryJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.StockEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.StockJpaRepository
import com.quantjumpstock.core.domain.model.stock.DesignationStatus
import com.quantjumpstock.core.domain.model.stock.Market
import com.quantjumpstock.core.domain.model.stock.Stock
import com.quantjumpstock.core.domain.model.stock.StockDesignationHistory
import com.quantjumpstock.core.domain.port.output.StockRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class StockPersistenceAdapter(
    private val stockJpaRepository: StockJpaRepository,
    private val historyJpaRepository: StockDesignationHistoryJpaRepository
) : StockRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun save(stock: Stock): Stock {
        logger.debug("종목 저장: ticker=${stock.ticker}")
        val entity = toEntity(stock)
        val saved = stockJpaRepository.save(entity)
        return toDomain(saved)
    }

    override fun findById(id: Long): Stock? {
        return stockJpaRepository.findById(id).orElse(null)?.let { toDomain(it) }
    }

    override fun findByTicker(ticker: String): Stock? {
        return stockJpaRepository.findByTicker(ticker)?.let { toDomain(it) }
    }

    override fun search(
        query: String?,
        market: Market?,
        sector: String?,
        isActive: Boolean?,
        pageable: Pageable
    ): Page<Stock> {
        return stockJpaRepository.search(
            query = query,
            market = market?.name,
            sector = sector,
            isActive = isActive,
            pageable = pageable
        ).map { toDomain(it) }
    }

    override fun findAll(): List<Stock> {
        return stockJpaRepository.findAll().map { toDomain(it) }
    }

    override fun findAllByIds(ids: List<Long>): List<Stock> {
        return stockJpaRepository.findAllById(ids).map { toDomain(it) }
    }

    override fun deleteById(id: Long) {
        logger.debug("종목 삭제: id=$id")
        stockJpaRepository.deleteById(id)
    }

    override fun existsById(id: Long): Boolean {
        return stockJpaRepository.existsById(id)
    }

    override fun saveDesignationHistory(history: StockDesignationHistory): StockDesignationHistory {
        val entity = toHistoryEntity(history)
        val saved = historyJpaRepository.save(entity)
        return toHistoryDomain(saved)
    }

    override fun findDesignationHistoryByStockId(stockId: Long): List<StockDesignationHistory> {
        return historyJpaRepository.findByStockIdOrderByChangedAtDesc(stockId)
            .map { toHistoryDomain(it) }
    }

    // ===== Mapping Functions =====

    private fun toDomain(entity: StockEntity): Stock {
        return Stock(
            id = entity.id,
            ticker = entity.ticker,
            stockName = entity.stockName,
            stockNameEn = entity.stockNameEn,
            exchange = entity.exchange,
            sector = entity.sector,
            industry = entity.industry,
            market = try {
                Market.valueOf(entity.market)
            } catch (e: Exception) {
                logger.warn("알 수 없는 Market 값: '${entity.market}' (ticker=${entity.ticker}), 기본값 KR 사용")
                Market.KR
            },
            isEtf = entity.isEtf,
            leverageTicker = entity.leverageTicker,
            designationStatus = try {
                DesignationStatus.valueOf(entity.designationStatus)
            } catch (e: Exception) {
                logger.warn("알 수 없는 DesignationStatus 값: '${entity.designationStatus}' (ticker=${entity.ticker}), 기본값 NORMAL 사용")
                DesignationStatus.NORMAL
            },
            designationReason = entity.designationReason,
            designatedAt = entity.designatedAt,
            isActive = entity.isActive,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun toEntity(domain: Stock): StockEntity {
        return StockEntity(
            id = domain.id,
            ticker = domain.ticker,
            stockName = domain.stockName,
            stockNameEn = domain.stockNameEn,
            exchange = domain.exchange,
            sector = domain.sector,
            industry = domain.industry,
            market = domain.market.name,
            isEtf = domain.isEtf,
            leverageTicker = domain.leverageTicker,
            designationStatus = domain.designationStatus.name,
            designationReason = domain.designationReason,
            designatedAt = domain.designatedAt,
            isActive = domain.isActive,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }

    private fun toHistoryDomain(entity: StockDesignationHistoryEntity): StockDesignationHistory {
        return StockDesignationHistory(
            id = entity.id,
            stockId = entity.stockId,
            previousStatus = DesignationStatus.valueOf(entity.previousStatus),
            newStatus = DesignationStatus.valueOf(entity.newStatus),
            reason = entity.reason,
            changedBy = entity.changedBy,
            changedAt = entity.changedAt
        )
    }

    private fun toHistoryEntity(domain: StockDesignationHistory): StockDesignationHistoryEntity {
        return StockDesignationHistoryEntity(
            id = domain.id,
            stockId = domain.stockId,
            previousStatus = domain.previousStatus.name,
            newStatus = domain.newStatus.name,
            reason = domain.reason,
            changedBy = domain.changedBy,
            changedAt = domain.changedAt
        )
    }
}
