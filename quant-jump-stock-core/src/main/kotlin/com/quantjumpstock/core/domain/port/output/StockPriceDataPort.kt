package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.stock.StockPriceSnapshot

interface StockPriceDataPort {
    fun getLatestPrice(ticker: String): StockPriceSnapshot?
    fun getLatestPrices(tickers: List<String>): Map<String, StockPriceSnapshot>
}
