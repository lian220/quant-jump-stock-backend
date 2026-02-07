package com.quantjumpstock.core.domain.model.strategy

import java.math.BigDecimal

/**
 * 전략 조건 블록 - sealed class
 *
 * SCRUM-166에서 정의한 7가지 조건 타입을 타입 안전하게 표현.
 * Python Data Engine DSL의 Condition과 호환되며, JSON 직렬화/역직렬화는
 * ConditionsParser(SCRUM-200)에서 처리.
 *
 * 각 서브클래스는:
 * - type: DSL 타입 판별자 (JSON discriminator)
 * - init 블록에서 비즈니스 규칙 검증
 */
sealed class ConditionBlock {
    abstract val type: String

    /**
     * SMA/EMA 이동평균선 교차 조건
     *
     * 예: 20일 SMA가 50일 SMA를 상향 돌파
     */
    data class SmaCrossover(
        val shortPeriod: Int,
        val longPeriod: Int,
        val movingAverageType: IndicatorType = IndicatorType.SMA,
        val crossDirection: ConditionOperator = ConditionOperator.CROSSES_ABOVE
    ) : ConditionBlock() {
        override val type: String = "SMA_CROSSOVER"

        init {
            require(shortPeriod > 0) { "shortPeriod must be positive" }
            require(longPeriod > 0) { "longPeriod must be positive" }
            require(shortPeriod < longPeriod) {
                "shortPeriod($shortPeriod) must be less than longPeriod($longPeriod)"
            }
            require(movingAverageType == IndicatorType.SMA || movingAverageType == IndicatorType.EMA) {
                "movingAverageType must be SMA or EMA, got: $movingAverageType"
            }
            require(crossDirection.isCrossOperator()) {
                "crossDirection must be CROSSES_ABOVE or CROSSES_BELOW, got: $crossDirection"
            }
        }
    }

    /**
     * RSI 필터 조건
     *
     * 예: RSI(14) < 30 (과매도)
     */
    data class RsiFilter(
        val period: Int = 14,
        val operator: ConditionOperator,
        val threshold: BigDecimal
    ) : ConditionBlock() {
        override val type: String = "RSI_FILTER"

        init {
            require(period > 0) { "period must be positive" }
            require(!operator.isCrossOperator()) {
                "RSI filter does not support cross operators, got: $operator"
            }
            require(threshold >= BigDecimal.ZERO && threshold <= BigDecimal("100")) {
                "RSI threshold must be between 0 and 100, got: $threshold"
            }
        }
    }

    /**
     * MACD 시그널 교차 조건
     *
     * 예: MACD(12,26,9) 시그널선 상향 돌파
     */
    data class MacdSignal(
        val shortPeriod: Int = 12,
        val longPeriod: Int = 26,
        val signalPeriod: Int = 9,
        val crossDirection: ConditionOperator = ConditionOperator.CROSSES_ABOVE
    ) : ConditionBlock() {
        override val type: String = "MACD_SIGNAL"

        init {
            require(shortPeriod > 0) { "shortPeriod must be positive" }
            require(longPeriod > 0) { "longPeriod must be positive" }
            require(signalPeriod > 0) { "signalPeriod must be positive" }
            require(shortPeriod < longPeriod) {
                "shortPeriod($shortPeriod) must be less than longPeriod($longPeriod)"
            }
            require(crossDirection.isCrossOperator()) {
                "crossDirection must be CROSSES_ABOVE or CROSSES_BELOW, got: $crossDirection"
            }
        }
    }

    /**
     * PER(주가수익비율) 필터 조건
     *
     * 예: PER < 15
     */
    data class PerFilter(
        val operator: ConditionOperator,
        val threshold: BigDecimal
    ) : ConditionBlock() {
        override val type: String = "PER_FILTER"

        init {
            require(!operator.isCrossOperator()) {
                "PER filter does not support cross operators, got: $operator"
            }
            require(threshold > BigDecimal.ZERO) {
                "PER threshold must be positive, got: $threshold"
            }
        }
    }

    /**
     * PBR(주가순자산비율) 필터 조건
     *
     * 예: PBR < 1.5
     */
    data class PbrFilter(
        val operator: ConditionOperator,
        val threshold: BigDecimal
    ) : ConditionBlock() {
        override val type: String = "PBR_FILTER"

        init {
            require(!operator.isCrossOperator()) {
                "PBR filter does not support cross operators, got: $operator"
            }
            require(threshold > BigDecimal.ZERO) {
                "PBR threshold must be positive, got: $threshold"
            }
        }
    }

    /**
     * 배당수익률 필터 조건
     *
     * 예: 배당수익률 >= 3%
     */
    data class DividendYield(
        val operator: ConditionOperator,
        val threshold: BigDecimal
    ) : ConditionBlock() {
        override val type: String = "DIVIDEND_YIELD"

        init {
            require(!operator.isCrossOperator()) {
                "DividendYield filter does not support cross operators, got: $operator"
            }
            require(threshold >= BigDecimal.ZERO && threshold <= BigDecimal("100")) {
                "DividendYield threshold must be between 0 and 100, got: $threshold"
            }
        }
    }

    /**
     * 포트폴리오 배분 조건
     *
     * 예: AAPL 목표 비중 30%, 허용 오차 5%
     */
    data class Allocation(
        val targetTicker: String? = null,
        val targetWeightPct: BigDecimal,
        val tolerance: BigDecimal = BigDecimal("5.0")
    ) : ConditionBlock() {
        override val type: String = "ALLOCATION"

        init {
            require(targetWeightPct >= BigDecimal.ZERO && targetWeightPct <= BigDecimal("100")) {
                "targetWeightPct must be between 0 and 100, got: $targetWeightPct"
            }
            require(tolerance >= BigDecimal.ZERO) {
                "tolerance must be non-negative, got: $tolerance"
            }
        }
    }
}
