package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.stock.Market
import com.quantjumpstock.core.domain.model.stock.Stock
import com.quantjumpstock.core.domain.model.stock.StockDesignationHistory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Stock Repository Port - 도메인 포트
 *
 * 도메인 모델(Stock)만 사용
 * 구현은 adapter/output/persistence/에서 수행
 */
interface StockRepository {

    fun save(stock: Stock): Stock

    fun findById(id: Long): Stock?

    fun findByTicker(ticker: String): Stock?

    fun search(
        query: String?,
        market: Market?,
        sector: String?,
        isActive: Boolean?,
        pageable: Pageable
    ): Page<Stock>

    fun findAll(): List<Stock>

    fun findAllByIds(ids: List<Long>): List<Stock>

    fun deleteById(id: Long)

    fun existsById(id: Long): Boolean

    fun saveDesignationHistory(history: StockDesignationHistory): StockDesignationHistory

    fun findDesignationHistoryByStockId(stockId: Long): List<StockDesignationHistory>
}
