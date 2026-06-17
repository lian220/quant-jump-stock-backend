package com.quantjumpstock.core.application.stock

import com.quantjumpstock.core.adapter.output.external.DataEngineClient
import com.quantjumpstock.core.domain.model.stock.Market
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
    private val dataEngineClient = mock<DataEngineClient>()
    private val service = StockService(stockRepository, priceDataPort, dataEngineClient)

    @Test
    fun `없는 종목이면 StockException`() {
        whenever(stockRepository.findById(99L)).thenReturn(null)

        assertThrows<StockException> { service.getPriceHistory(99L, "1m") }
    }

    @Test
    fun `존재하는 종목은 data-engine 응답을 전달`() {
        val stock = Stock(id = 1L, ticker = "AAPL", stockName = "Apple Inc.", market = Market.US)
        whenever(stockRepository.findById(1L)).thenReturn(stock)
        whenever(dataEngineClient.getPriceHistory("AAPL", "1m"))
            .thenReturn(PriceHistoryResponse(ticker = "AAPL", period = "1m", candles = emptyList()))

        val result = service.getPriceHistory(1L, "1m")

        assertEquals("AAPL", result.ticker)
        assertEquals("1m", result.period)
    }
}
