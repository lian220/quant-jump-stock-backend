package com.quantjumpstock.core.application.stock

import com.quantjumpstock.core.domain.model.stock.Market
import com.quantjumpstock.core.domain.model.stock.PriceHistoryPeriod
import com.quantjumpstock.core.domain.model.stock.Stock
import com.quantjumpstock.core.domain.port.output.StockPriceDataPort
import com.quantjumpstock.core.domain.port.output.StockRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * StockService.getPriceHistory 단위 테스트 (thin proxy)
 */
@DisplayName("StockService price-history 단위 테스트")
class StockPriceHistoryServiceTest {
    private val stockRepository = mock<StockRepository>()
    private val priceDataPort = mock<StockPriceDataPort>()
    private val priceHistoryPort = mock<PriceHistoryPort>()
    private val service = StockService(stockRepository, priceDataPort, priceHistoryPort)

    @Test
    fun `없는 종목이면 StockException`() {
        whenever(stockRepository.findById(99L)).thenReturn(null)

        assertThrows<StockException> { service.getPriceHistory(99L, PriceHistoryPeriod.ONE_MONTH) }
    }

    @Test
    fun `존재하는 종목은 data-engine 응답을 전달`() {
        val stock = Stock(id = 1L, ticker = "AAPL", stockName = "Apple Inc.", market = Market.US)
        whenever(stockRepository.findById(1L)).thenReturn(stock)
        whenever(priceHistoryPort.getPriceHistory("AAPL", PriceHistoryPeriod.ONE_MONTH))
            .thenReturn(PriceHistoryResponse(ticker = "AAPL", period = "1m", candles = emptyList()))

        val result = service.getPriceHistory(1L, PriceHistoryPeriod.ONE_MONTH)

        assertEquals("AAPL", result.ticker)
        assertEquals("1m", result.period)
    }
}
