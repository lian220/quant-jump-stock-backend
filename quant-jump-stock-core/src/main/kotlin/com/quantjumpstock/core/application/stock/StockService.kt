package com.quantjumpstock.core.application.stock

import com.quantjumpstock.core.domain.model.stock.Market
import com.quantjumpstock.core.domain.model.stock.Stock
import com.quantjumpstock.core.domain.model.stock.StockDesignationHistory
import com.quantjumpstock.core.domain.port.output.StockRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class StockService(
    private val stockRepository: StockRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 종목 검색/목록 (페이징)
     */
    fun searchStocks(
        query: String?,
        market: Market?,
        sector: String?,
        isActive: Boolean?,
        pageable: Pageable
    ): StockSearchResponse {
        logger.info("종목 검색: query=$query, market=$market, sector=$sector")

        val page = stockRepository.search(query, market, sector, isActive, pageable)

        return StockSearchResponse(
            stocks = page.content.map { it.toSummary() },
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            currentPage = page.number
        )
    }

    /**
     * 종목 상세 조회
     */
    fun getStock(stockId: Long): StockDetailResponse {
        val stock = stockRepository.findById(stockId)
            ?: throw StockException("종목을 찾을 수 없습니다: $stockId")

        return stock.toDetailResponse()
    }

    /**
     * 종목 등록 (관리자)
     */
    @Transactional
    fun createStock(request: CreateStockRequest): StockResponse {
        logger.info("종목 등록: ticker=${request.ticker}")

        // 티커 중복 체크
        stockRepository.findByTicker(request.ticker)?.let {
            throw StockException("이미 등록된 종목 코드입니다: ${request.ticker}")
        }

        val stock = Stock(
            ticker = request.ticker,
            stockName = request.stockName,
            stockNameEn = request.stockNameEn,
            exchange = request.exchange,
            sector = request.sector,
            industry = request.industry,
            market = request.market,
            isEtf = request.isEtf,
            leverageTicker = request.leverageTicker
        )

        val saved = stockRepository.save(stock)
        logger.info("종목 등록 완료: id=${saved.id}, ticker=${saved.ticker}")

        return StockResponse(
            success = true,
            stockId = saved.id,
            message = "종목이 등록되었습니다"
        )
    }

    /**
     * 종목 수정 (관리자)
     */
    @Transactional
    fun updateStock(stockId: Long, request: UpdateStockRequest): StockResponse {
        logger.info("종목 수정: stockId=$stockId")

        val stock = stockRepository.findById(stockId)
            ?: throw StockException("종목을 찾을 수 없습니다: $stockId")

        val updated = stock.copy(
            stockName = request.stockName ?: stock.stockName,
            stockNameEn = request.stockNameEn ?: stock.stockNameEn,
            exchange = request.exchange ?: stock.exchange,
            sector = request.sector ?: stock.sector,
            industry = request.industry ?: stock.industry,
            market = request.market ?: stock.market,
            isEtf = request.isEtf ?: stock.isEtf,
            leverageTicker = request.leverageTicker ?: stock.leverageTicker,
            isActive = request.isActive ?: stock.isActive
        )

        stockRepository.save(updated)
        logger.info("종목 수정 완료: stockId=$stockId")

        return StockResponse(
            success = true,
            stockId = stockId,
            message = "종목이 수정되었습니다"
        )
    }

    /**
     * 종목 삭제 (관리자)
     */
    @Transactional
    fun deleteStock(stockId: Long): StockResponse {
        logger.info("종목 삭제: stockId=$stockId")

        if (!stockRepository.existsById(stockId)) {
            throw StockException("종목을 찾을 수 없습니다: $stockId")
        }

        stockRepository.deleteById(stockId)
        logger.info("종목 삭제 완료: stockId=$stockId")

        return StockResponse(
            success = true,
            stockId = stockId,
            message = "종목이 삭제되었습니다"
        )
    }

    /**
     * 지정 상태 변경 (관리자)
     */
    @Transactional
    fun changeDesignation(stockId: Long, request: ChangeDesignationRequest, changedBy: Long?): StockResponse {
        logger.info("종목 지정상태 변경: stockId=$stockId, newStatus=${request.designationStatus}")

        val stock = stockRepository.findById(stockId)
            ?: throw StockException("종목을 찾을 수 없습니다: $stockId")

        // 도메인 모델에서 비즈니스 검증 + 상태 변경
        val updated = stock.changeDesignation(request.designationStatus, request.reason)
        stockRepository.save(updated)

        // 이력 저장
        val history = StockDesignationHistory(
            stockId = stockId,
            previousStatus = stock.designationStatus,
            newStatus = request.designationStatus,
            reason = request.reason,
            changedBy = changedBy
        )
        stockRepository.saveDesignationHistory(history)

        logger.info("종목 지정상태 변경 완료: stockId=$stockId, ${stock.designationStatus} -> ${request.designationStatus}")

        return StockResponse(
            success = true,
            stockId = stockId,
            message = "지정 상태가 변경되었습니다"
        )
    }

    /**
     * 지정 상태 변경 이력 조회
     */
    fun getDesignationHistory(stockId: Long): List<DesignationHistoryResponse> {
        if (!stockRepository.existsById(stockId)) {
            throw StockException("종목을 찾을 수 없습니다: $stockId")
        }

        return stockRepository.findDesignationHistoryByStockId(stockId).map {
            DesignationHistoryResponse(
                id = it.id!!,
                previousStatus = it.previousStatus,
                newStatus = it.newStatus,
                reason = it.reason,
                changedBy = it.changedBy,
                changedAt = it.changedAt
            )
        }
    }

    // ===== Mapping Extensions =====

    private fun Stock.toSummary(): StockSummary {
        return StockSummary(
            id = this.id!!,
            ticker = this.ticker,
            stockName = this.stockName,
            stockNameEn = this.stockNameEn,
            market = this.market,
            sector = this.sector,
            isEtf = this.isEtf,
            designationStatus = this.designationStatus,
            isActive = this.isActive
        )
    }

    private fun Stock.toDetailResponse(): StockDetailResponse {
        return StockDetailResponse(
            id = this.id!!,
            ticker = this.ticker,
            stockName = this.stockName,
            stockNameEn = this.stockNameEn,
            exchange = this.exchange,
            sector = this.sector,
            industry = this.industry,
            market = this.market,
            isEtf = this.isEtf,
            leverageTicker = this.leverageTicker,
            designationStatus = this.designationStatus,
            designationReason = this.designationReason,
            designatedAt = this.designatedAt,
            isActive = this.isActive,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }
}
