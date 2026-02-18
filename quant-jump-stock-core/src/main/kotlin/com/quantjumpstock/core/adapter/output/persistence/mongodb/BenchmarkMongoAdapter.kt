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
    private val mongoTemplate: MongoTemplate,
    private val jdbcTemplate: org.springframework.jdbc.core.JdbcTemplate
) : BenchmarkDataPort {

    private val log = LoggerFactory.getLogger(javaClass)
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    // ticker → 한글명 매핑 캐시 (애플리케이션 시작 시 한 번만 로드)
    private val tickerToNameMap: Map<String, String> by lazy {
        jdbcTemplate.query("SELECT ticker, name FROM yfinance_indicators WHERE is_active = true") { rs, _ ->
            rs.getString("ticker") to rs.getString("name")
        }.toMap()
    }

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
                // ETF는 stocks에 주로 저장, yfinance_indicators에도 있을 수 있음
                query.fields().include("stocks.$ticker.close")
                query.fields().include("stocks.$ticker.close_price")
                query.fields().include("yfinance_indicators")
            }
            BenchmarkType.INDEX, BenchmarkType.COMMODITY, BenchmarkType.CURRENCY -> {
                query.fields().include("yfinance_indicators")
            }
            BenchmarkType.STRATEGY -> {
                // STRATEGY 타입은 MongoDB에서 조회하지 않음 (별도 처리)
                log.debug("STRATEGY type does not use MongoDB. Returning empty list.")
                return emptyList()
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
        // 1. yfinance_indicators에서 먼저 검색 (ticker 또는 한글 이름)
        val yf = doc["yfinance_indicators"] as? Map<String, Any>
        if (yf != null) {
            // ticker로 검색
            val valueByTicker = yf[ticker]
            if (valueByTicker != null) {
                val result = extractNumericValue(valueByTicker)
                if (result != null) return result
            }

            // 한글 이름으로 검색 (데이터에 한글로 저장된 경우)
            val displayName = getDisplayName(ticker)
            if (displayName != null) {
                val valueByName = yf[displayName]
                if (valueByName != null) {
                    val result = extractNumericValue(valueByName)
                    if (result != null) return result
                }
            }
        }

        // 2. ETF의 경우 stocks 컬렉션에서도 검색
        if (type == BenchmarkType.ETF) {
            val stocks = doc["stocks"] as? Map<String, Any>
            if (stocks != null) {
                val stockData = stocks[ticker] as? Map<String, Any>
                if (stockData != null) {
                    val close = stockData["close"] ?: stockData["close_price"]
                    val result = close?.let { toBigDecimal(it) }
                    if (result != null) return result
                }
            }
        }

        return null
    }

    /**
     * ticker에 대응하는 한글 표시명 반환 (PostgreSQL yfinance_indicators 테이블 조회)
     */
    private fun getDisplayName(ticker: String): String? {
        return tickerToNameMap[ticker]
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
