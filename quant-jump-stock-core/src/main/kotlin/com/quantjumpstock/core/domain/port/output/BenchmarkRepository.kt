package com.quantjumpstock.core.domain.port.output

/**
 * 지원되는 벤치마크 목록 (코드 상수)
 *
 * MongoDB daily_stock_data에 실제 데이터가 존재하는 벤치마크만 등록합니다.
 * - stocks: SPY, QQQ, SOXX (OHLCV 전체)
 * - yfinance_indicators: ^GSPC, ^NDX, ^VIX 등 (close만)
 */
enum class Benchmark(
    val ticker: String,
    val displayName: String,
    val type: BenchmarkType
) {
    // === ETF (stocks 컬렉션에서 OHLCV 로드) ===
    SPY("SPY", "S&P 500 ETF", BenchmarkType.ETF),
    QQQ("QQQ", "나스닥 100 ETF", BenchmarkType.ETF),
    SOXX("SOXX", "반도체 ETF", BenchmarkType.ETF),
    DIA("DIA", "다우 존스 ETF", BenchmarkType.ETF),
    IWM("IWM", "러셀 2000 ETF", BenchmarkType.ETF),
    AGG("AGG", "미국 채권시장 ETF", BenchmarkType.ETF),
    VNQ("VNQ", "미국 리츠 ETF", BenchmarkType.ETF),

    // === 지수 (yfinance_indicators 컬렉션에서 close 로드) ===
    GSPC("^GSPC", "S&P 500 지수", BenchmarkType.INDEX),
    NDX("^NDX", "나스닥 100 지수", BenchmarkType.INDEX),
    VIX("^VIX", "VIX 변동성 지수", BenchmarkType.INDEX),
    N225("^N225", "닛케이 225", BenchmarkType.INDEX),
    HSI("^HSI", "항셍 지수", BenchmarkType.INDEX),
    FTSE("^FTSE", "영국 FTSE 100", BenchmarkType.INDEX),
    GDAXI("^GDAXI", "독일 DAX", BenchmarkType.INDEX),
    FCHI("^FCHI", "프랑스 CAC 40", BenchmarkType.INDEX),
    SSE("000001.SS", "상해종합 지수", BenchmarkType.INDEX),
    KS11("^KS11", "KOSPI 지수", BenchmarkType.INDEX),
    KQ11("^KQ11", "KOSDAQ 지수", BenchmarkType.INDEX),

    // === 원자재/통화 ===
    GOLD("GC=F", "금 가격", BenchmarkType.COMMODITY),
    DXY("DX-Y.NYB", "달러 인덱스", BenchmarkType.CURRENCY);

    companion object {
        /** 기본 벤치마크 */
        val DEFAULT = SPY
        val DEFAULT_TICKER: String = DEFAULT.ticker

        /** STRATEGY 벤치마크 접두사 (예: "STRATEGY:105") */
        const val STRATEGY_PREFIX = "STRATEGY:"

        /** 다중 벤치마크 최대 개수 */
        const val MAX_BENCHMARKS = 3

        fun findByTicker(ticker: String): Benchmark? =
            entries.find { it.ticker == ticker }

        fun existsByTicker(ticker: String): Boolean =
            entries.any { it.ticker == ticker }

        /** 전략 벤치마크 여부 확인 (STRATEGY:ID 형태) */
        fun isStrategyBenchmark(ticker: String): Boolean =
            ticker.startsWith(STRATEGY_PREFIX)

        /** 전략 벤치마크에서 전략 ID 추출 */
        fun parseStrategyId(ticker: String): Long? =
            if (isStrategyBenchmark(ticker)) {
                ticker.removePrefix(STRATEGY_PREFIX).toLongOrNull()
            } else null

        /** 벤치마크 유효성 검증 (일반 + STRATEGY 모두 지원) */
        fun isValidBenchmark(ticker: String): Boolean =
            existsByTicker(ticker) || (isStrategyBenchmark(ticker) && parseStrategyId(ticker) != null)

        fun getAll(): List<BenchmarkInfo> =
            entries.map { BenchmarkInfo(it.ticker, it.displayName, it.type.value) }
    }
}

enum class BenchmarkType(val value: String) {
    ETF("etf"),
    INDEX("index"),
    COMMODITY("commodity"),
    CURRENCY("currency"),
    STRATEGY("strategy")   // SCRUM-337: 타 전략 간 비교
}

/**
 * 벤치마크 정보 (API 응답용)
 */
data class BenchmarkInfo(
    val ticker: String,
    val name: String,
    val type: String
)
