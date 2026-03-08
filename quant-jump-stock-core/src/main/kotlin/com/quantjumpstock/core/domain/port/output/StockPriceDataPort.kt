package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.stock.StockPriceSnapshot

interface StockPriceDataPort {
    fun getLatestPrice(ticker: String): StockPriceSnapshot?
    fun getLatestPrices(tickers: List<String>): Map<String, StockPriceSnapshot>

    /**
     * yfinance_indicators에서 시장 지수 최신 데이터 조회
     * @param tickers 지수 ticker 목록 (예: ^GSPC, ^NDX, ^VIX)
     * @return ticker → MarketIndexSnapshot 매핑
     */
    fun getLatestMarketIndices(tickers: List<String>): Map<String, MarketIndexSnapshot>
}

data class MarketIndexSnapshot(
    val ticker: String,
    val name: String?,
    val date: java.time.LocalDate,
    val price: java.math.BigDecimal,
    val previousClose: java.math.BigDecimal?,
    val changePercent: java.math.BigDecimal?
)
