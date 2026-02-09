package com.quantjumpstock.core.application.stock

import com.quantjumpstock.core.adapter.output.persistence.jpa.StockEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.StockJpaRepository
import com.quantjumpstock.core.domain.model.stock.DesignationStatus
import com.quantjumpstock.core.domain.model.stock.Market
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.format.DateTimeFormatter

@Service
@Transactional(readOnly = true)
class StockSearchService(
    private val stockJpaRepository: StockJpaRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun searchStocks(request: StockSearchRequest): StockSearchResponse {
        logger.info("종목 검색: query=${request.query}, market=${request.market}, page=${request.page}, size=${request.size}")

        val pageable = PageRequest.of(request.page, request.size)
        val page = stockJpaRepository.search(
            query = request.query,
            market = request.market?.name,
            sector = null,
            isActive = request.isActive,
            pageable = pageable
        )

        val stocks = page.content.map { entity -> entity.toSummaryDto() }

        logger.info("종목 검색 완료: 총 ${page.totalElements}개, ${page.totalPages} 페이지")

        return StockSearchResponse(
            stocks = stocks,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            currentPage = page.number
        )
    }

    fun getStockDetail(id: Long): StockDetailResponse {
        logger.info("종목 상세 조회: id=$id")

        val stock = stockJpaRepository.findById(id)
            .orElseThrow { NoSuchElementException("종목을 찾을 수 없습니다: id=$id") }

        logger.info("종목 상세 조회 완료: ${stock.ticker}")

        return stock.toDetailDto()
    }

    private fun StockEntity.toSummaryDto(): StockSummary {
        return StockSummary(
            id = this.id!!,
            ticker = this.ticker,
            stockName = this.stockName,
            stockNameEn = this.stockNameEn,
            market = Market.valueOf(this.market),
            sector = this.sector,
            isEtf = this.isEtf,
            designationStatus = DesignationStatus.valueOf(this.designationStatus),
            isActive = this.isActive
        )
    }

    private fun StockEntity.toDetailDto(): StockDetailResponse {
        return StockDetailResponse(
            id = this.id!!,
            ticker = this.ticker,
            stockName = this.stockName,
            stockNameEn = this.stockNameEn,
            exchange = this.exchange,
            sector = this.sector,
            industry = this.industry,
            market = Market.valueOf(this.market),
            isEtf = this.isEtf,
            leverageTicker = this.leverageTicker,
            designationStatus = DesignationStatus.valueOf(this.designationStatus),
            designationReason = this.designationReason,
            designatedAt = this.designatedAt,
            isActive = this.isActive,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }
}
