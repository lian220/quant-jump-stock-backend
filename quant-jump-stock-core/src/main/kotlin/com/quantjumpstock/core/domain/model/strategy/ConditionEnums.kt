package com.quantjumpstock.core.domain.model.strategy

/**
 * 기술적 지표 유형 - Python DSL IndicatorType과 1:1 매핑
 *
 * @property dslCode JSON DSL에서 사용하는 코드 (Python enum value와 동일)
 */
enum class IndicatorType(val dslCode: String) {
    SMA("sma"),
    EMA("ema"),
    RSI("rsi"),
    MACD("macd"),
    MACD_SIGNAL("macd_signal"),
    MACD_HIST("macd_hist"),
    BOLLINGER_UPPER("bollinger_upper"),
    BOLLINGER_MIDDLE("bollinger_middle"),
    BOLLINGER_LOWER("bollinger_lower"),
    VOLUME("volume"),
    PRICE("price"),
    PER("per"),
    PBR("pbr"),
    DIVIDEND_YIELD("dividend_yield"),
    // 펀더멘탈 지표 (신규)
    ROE("roe"),
    EARNINGS_GROWTH("earnings_growth"),
    DEBT_TO_EQUITY("debt_to_equity"),
    FORWARD_PE("forward_pe");

    companion object {
        private val FUNDAMENTAL_CODES = setOf(
            "per", "pbr", "dividend_yield", "roe", "earnings_growth", "debt_to_equity", "forward_pe"
        )

        fun fromDslCode(code: String): IndicatorType =
            entries.find { it.dslCode == code }
                ?: throw IllegalArgumentException("Unknown IndicatorType dslCode: $code")

        fun isFundamentalIndicator(code: String): Boolean = code in FUNDAMENTAL_CODES
    }
}

/**
 * 조건 연산자 - Python DSL ConditionOperator와 1:1 매핑
 */
enum class ConditionOperator(val dslCode: String) {
    GT("gt"),
    GTE("gte"),
    LT("lt"),
    LTE("lte"),
    EQ("eq"),
    NEQ("neq"),
    CROSSES_ABOVE("crosses_above"),
    CROSSES_BELOW("crosses_below");

    fun isCrossOperator(): Boolean = this == CROSSES_ABOVE || this == CROSSES_BELOW

    companion object {
        fun fromDslCode(code: String): ConditionOperator =
            entries.find { it.dslCode == code }
                ?: throw IllegalArgumentException("Unknown ConditionOperator dslCode: $code")
    }
}

/**
 * 매매 신호 유형 - Python DSL SignalType과 1:1 매핑
 */
enum class SignalType(val dslCode: String) {
    BUY("buy"),
    SELL("sell"),
    HOLD("hold");

    companion object {
        fun fromDslCode(code: String): SignalType =
            entries.find { it.dslCode == code }
                ?: throw IllegalArgumentException("Unknown SignalType dslCode: $code")
    }
}

/**
 * 조건 결합 논리 - Python DSL Rule.logic 매핑
 */
enum class ConditionLogic(val dslCode: String) {
    AND("and"),
    OR("or");

    companion object {
        fun fromDslCode(code: String): ConditionLogic =
            entries.find { it.dslCode == code }
                ?: throw IllegalArgumentException("Unknown ConditionLogic dslCode: $code")
    }
}
