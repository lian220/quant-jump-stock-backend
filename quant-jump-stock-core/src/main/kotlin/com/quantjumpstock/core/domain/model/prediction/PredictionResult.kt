package com.quantjumpstock.core.domain.model.prediction

import java.math.BigDecimal
import java.time.LocalDate

/**
 * 통합 예측 결과 Domain 모델
 *
 * AI 예측 + 감정 분석 + 기술적 분석을 통합한 종합 예측 결과.
 * Composite Score 기반으로 매수 신호를 판단함.
 */
data class PredictionResult(
    // 종목 정보
    val ticker: String,
    val stockName: String,
    val analysisDate: LocalDate,

    // AI 예측 점수
    val aiPredictedPrice: BigDecimal? = null,
    val aiRiseProbability: BigDecimal? = null,
    val aiScore: BigDecimal = BigDecimal.ZERO,

    // 감정 분석 점수
    val sentimentScore: BigDecimal? = null,
    val sentimentNewsCount: Int? = null,
    val sentimentNormalizedScore: BigDecimal = BigDecimal.ZERO,

    // 기술적 분석 점수
    val techSma20: BigDecimal? = null,
    val techSma50: BigDecimal? = null,
    val techRsi: BigDecimal? = null,
    val techMacd: BigDecimal? = null,
    val techSignal: BigDecimal? = null,
    val techGoldenCross: Boolean = false,
    val techMacdBuySignal: Boolean = false,
    val techScore: BigDecimal = BigDecimal.ZERO,
    val techSignalsCount: Int = 0,

    // Composite Score
    val compositeScore: BigDecimal,
    val compositeGrade: CompositeGrade,

    // 추천 여부
    val isRecommended: Boolean = false,
    val recommendationReason: String? = null,

    // 가격 메트릭 (Phase 6.5 추가)
    val currentPrice: BigDecimal? = null,       // 현재가 (최신 종가)
    val targetPrice: BigDecimal? = null,        // 목표가 (AI 예측 가격, aiPredictedPrice와 동일)
    val upsidePercent: BigDecimal? = null,      // 상승여력 (%)
    val priceRecommendation: String? = null     // 가격 추천 (강력매수/매수/보유/매도)
) {
    companion object {
        /**
         * Composite Score 계산
         */
        fun calculateCompositeScore(
            aiScore: BigDecimal,
            techScore: BigDecimal,
            sentimentScore: BigDecimal
        ): BigDecimal {
            val weightAi = BigDecimal("0.3")
            val weightTech = BigDecimal("0.4")
            val weightSentiment = BigDecimal("0.3")

            return (aiScore * weightAi)
                .add(techScore * weightTech)
                .add(sentimentScore * weightSentiment)
                .setScale(2, java.math.RoundingMode.HALF_UP)
        }

        /**
         * 등급 판정
         */
        fun determineGrade(compositeScore: BigDecimal): CompositeGrade {
            return when {
                compositeScore >= BigDecimal("6.0") -> CompositeGrade.S
                compositeScore >= BigDecimal("4.5") -> CompositeGrade.A
                compositeScore >= BigDecimal("3.0") -> CompositeGrade.B
                compositeScore >= BigDecimal("1.5") -> CompositeGrade.C
                else -> CompositeGrade.D
            }
        }
    }
}

/**
 * Composite Score 등급
 */
enum class CompositeGrade(
    val range: String,
    val description: String,
    val color: String
) {
    S("6.0 ~ 7.5", "매우 강한 매수 신호", "emerald"),
    A("4.5 ~ 5.9", "강한 매수 신호", "cyan"),
    B("3.0 ~ 4.4", "보통 매수 신호", "yellow"),
    C("1.5 ~ 2.9", "약한 매수 신호", "orange"),
    D("0.0 ~ 1.4", "매수 신호 없음", "red");

    companion object {
        fun fromString(value: String): CompositeGrade {
            return entries.find { it.name == value.uppercase() }
                ?: throw IllegalArgumentException("Invalid grade: $value")
        }
    }
}
