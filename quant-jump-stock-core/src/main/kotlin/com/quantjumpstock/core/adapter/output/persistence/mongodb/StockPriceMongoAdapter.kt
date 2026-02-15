package com.quantjumpstock.core.adapter.output.persistence.mongodb

import com.quantjumpstock.core.domain.model.stock.StockPriceSnapshot
import com.quantjumpstock.core.domain.port.output.StockPriceDataPort
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

@Component
class StockPriceMongoAdapter(
    private val mongoTemplate: MongoTemplate
) : StockPriceDataPort {

    private val log = LoggerFactory.getLogger(javaClass)
    private val priceFields = listOf("open", "high", "low", "close", "close_price", "volume", "info")

    override fun getLatestPrice(ticker: String): StockPriceSnapshot? {
        val prices = getLatestPrices(listOf(ticker))
        return prices[ticker]
    }

    @Suppress("UNCHECKED_CAST")
    override fun getLatestPrices(tickers: List<String>): Map<String, StockPriceSnapshot> {
        if (tickers.isEmpty()) return emptyMap()

        return try {
            val query = Query()
                .addCriteria(Criteria.where("stocks").exists(true).ne(null))
                .with(Sort.by(Sort.Direction.DESC, "date"))
                .limit(5)

            // 필요한 종목의 필요한 필드만 projection (대용량 문서 최적화)
            val fields = query.fields().include("date")
            for (ticker in tickers) {
                for (field in priceFields) {
                    fields.include("stocks.$ticker.$field")
                }
            }

            val docs = mongoTemplate.find(query, Map::class.java, "daily_stock_data")

            // stocks가 비어있지 않은 문서만 필터 (수집 미완료 문서 제외)
            val validDocs = docs.filter { doc ->
                val stocks = doc["stocks"] as? Map<*, *>
                stocks != null && stocks.isNotEmpty()
            }
            if (validDocs.isEmpty()) return emptyMap()

            val latestDoc = validDocs[0]
            val previousDoc = validDocs.getOrNull(1)

            val latestDate = parseDate(latestDoc["date"]?.toString()) ?: return emptyMap()
            val latestStocks = latestDoc["stocks"] as? Map<String, Any> ?: return emptyMap()
            val previousStocks = previousDoc?.let { it["stocks"] as? Map<String, Any> }

            tickers.mapNotNull { ticker ->
                val stockData = latestStocks[ticker] as? Map<String, Any> ?: return@mapNotNull null
                val close = extractNumber(stockData["close"] ?: stockData["close_price"]) ?: return@mapNotNull null

                val previousClose = previousStocks?.let { prev ->
                    val prevData = prev[ticker] as? Map<String, Any>
                    prevData?.let { extractNumber(it["close"] ?: it["close_price"]) }
                }

                val info = stockData["info"] as? Map<String, Any>

                ticker to StockPriceSnapshot(
                    ticker = ticker,
                    date = latestDate,
                    open = extractNumber(stockData["open"]),
                    high = extractNumber(stockData["high"]),
                    low = extractNumber(stockData["low"]),
                    close = close,
                    volume = extractLong(stockData["volume"]),
                    previousClose = previousClose,
                    marketCap = info?.let { extractNumber(it["marketCap"]) },
                    trailingPE = info?.let { extractNumber(it["trailingPE"]) }
                )
            }.toMap()
        } catch (e: Exception) {
            log.warn("MongoDB 가격 데이터 조회 실패: {}", e.message)
            emptyMap()
        }
    }

    private fun parseDate(dateStr: String?): LocalDate? {
        return try {
            dateStr?.let { LocalDate.parse(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractNumber(value: Any?): BigDecimal? {
        return try {
            when (value) {
                is Number -> BigDecimal(value.toString())
                is String -> BigDecimal(value)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractLong(value: Any?): Long? {
        return try {
            when (value) {
                is Number -> value.toLong()
                is String -> value.toLong()
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
