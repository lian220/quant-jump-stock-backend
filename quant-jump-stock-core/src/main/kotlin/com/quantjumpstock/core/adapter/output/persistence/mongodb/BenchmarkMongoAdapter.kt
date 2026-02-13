package com.quantjumpstock.core.adapter.output.persistence.mongodb

import com.quantjumpstock.core.domain.model.benchmark.BenchmarkSeriesPoint
import com.quantjumpstock.core.domain.port.output.BenchmarkDataPort
import com.quantjumpstock.core.domain.port.output.BenchmarkType
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class BenchmarkMongoAdapter(
    private val mongoTemplate: MongoTemplate
) : BenchmarkDataPort {

    private val log = LoggerFactory.getLogger(javaClass)
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    override fun loadBenchmarkSeries(
        ticker: String,
        type: BenchmarkType,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<BenchmarkSeriesPoint> {
        val query = Query()
            .addCriteria(
                Criteria.where("date")
                    .gte(startDate.format(dateFormatter))
                    .lte(endDate.format(dateFormatter))
            )
            .with(Sort.by(Sort.Direction.ASC, "date"))

        // projection: date + 필요한 필드만
        query.fields().include("date")
        when (type) {
            BenchmarkType.ETF -> {
                query.fields().include("stocks.$ticker.close")
                query.fields().include("stocks.$ticker.close_price")
            }
            BenchmarkType.INDEX, BenchmarkType.COMMODITY, BenchmarkType.CURRENCY -> {
                query.fields().include("yfinance_indicators")
            }
        }

        val docs = mongoTemplate.find(query, Map::class.java, "daily_stock_data")

        return docs.mapNotNull { doc ->
            val dateStr = doc["date"]?.toString() ?: return@mapNotNull null
            val date = try {
                LocalDate.parse(dateStr)
            } catch (e: Exception) {
                return@mapNotNull null
            }

            val closeValue = extractCloseValue(doc, ticker, type)
            if (closeValue != null) {
                BenchmarkSeriesPoint(date = date, value = closeValue)
            } else {
                null
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractCloseValue(doc: Map<*, *>, ticker: String, type: BenchmarkType): BigDecimal? {
        return when (type) {
            BenchmarkType.ETF -> {
                val stocks = doc["stocks"] as? Map<String, Any> ?: return null
                val stockData = stocks[ticker] as? Map<String, Any> ?: return null
                val close = stockData["close"] ?: stockData["close_price"]
                close?.let { toBigDecimal(it) }
            }
            BenchmarkType.INDEX, BenchmarkType.COMMODITY, BenchmarkType.CURRENCY -> {
                val yf = doc["yfinance_indicators"] as? Map<String, Any> ?: return null
                // yfinance_indicators 키는 ticker (e.g., "^GSPC")
                val value = yf[ticker] ?: return null
                extractNumericValue(value)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractNumericValue(value: Any): BigDecimal? {
        return when (value) {
            is Map<*, *> -> {
                val mapValue = value as Map<String, Any>
                val close = mapValue["close"] ?: mapValue["close_price"]
                close?.let { toBigDecimal(it) }
            }
            else -> toBigDecimal(value)
        }
    }

    private fun toBigDecimal(value: Any): BigDecimal? {
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
}
