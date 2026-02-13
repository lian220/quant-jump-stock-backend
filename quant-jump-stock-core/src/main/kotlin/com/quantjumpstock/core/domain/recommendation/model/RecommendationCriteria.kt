package com.quantjumpstock.core.domain.recommendation.model

import java.math.BigDecimal

/**
 * 종목 추천 기준 상수
 *
 * ⭐ 모든 추천 관련 수치를 한 곳에 모아 관리 ⭐
 *
 * ## 점수 체계 개요
 * ```
 * Composite Score (0~7.5) = AI(30%) + 기술적분석(40%) + 감정분석(30%)
 *                         = (0.3 × AI) + (0.4 × Tech) + (0.3 × Sentiment)
 * ```
 *
 * ## 개별 점수 계산 방법
 *
 * ### 1. 기술적 분석 점수 (Tech Score, 최대 3.5점)
 * - Golden Cross (SMA20 > SMA50): **1.5점**
 * - RSI < 50 (과매도): **1.0점**
 * - MACD 매수 신호: **1.0점**
 *
 * ### 2. AI 예측 점수 (AI Score, 최대 2.5점)
 * - Vertex AI 상승 확률 기반
 * - 현재 미구현 (Phase 6 진행 중)
 *
 * ### 3. 감정 분석 점수 (Sentiment Score, 최대 2.5점)
 * - 뉴스 감정 분석 기반
 * - 현재 미구현 (Phase 6 진행 중)
 *
 * ## 현재 상태 (Tech-only)
 * ```
 * Composite Score (현재) = 0.4 × Tech Score
 * 최대 가능 점수 = 0.4 × 3.5 = 1.4점
 * ```
 *
 * 📖 상세 문서: `docs/features/recommendation-scoring-guide.md`
 *
 * 나중에 DB 설정으로 마이그레이션할 수 있도록 준비됨.
 */
object RecommendationCriteria {

    // ═════════════════════════════════════════════════
    // 1. 점수 계산 가중치
    // ═════════════════════════════════════════════════

    /** AI 예측 가중치 (30%) */
    val WEIGHT_AI = BigDecimal("0.3")

    /** 기술적 분석 가중치 (40%) */
    val WEIGHT_TECH = BigDecimal("0.4")

    /** 감정 분석 가중치 (30%) */
    val WEIGHT_SENTIMENT = BigDecimal("0.3")

    // ═════════════════════════════════════════════════
    // 2. 등급 임계값 (Composite Score 기준)
    // ═════════════════════════════════════════════════

    /** S등급 (최고) - 매우 강한 매수 신호 */
    val GRADE_S_THRESHOLD = BigDecimal("6.0")

    /** A등급 - 강한 매수 신호 */
    val GRADE_A_THRESHOLD = BigDecimal("4.5")

    /** B등급 - 보통 매수 신호 */
    val GRADE_B_THRESHOLD = BigDecimal("3.0")

    /** C등급 - 약한 매수 신호 */
    val GRADE_C_THRESHOLD = BigDecimal("1.5")

    // D등급: 1.5 미만

    // ═════════════════════════════════════════════════
    // 3. 추천 기준
    // ═════════════════════════════════════════════════

    /** 최소 추천 점수 (기본값) */
    val MIN_RECOMMEND_SCORE = BigDecimal("5.25")

    /** 최대 점수 */
    val MAX_SCORE = BigDecimal("7.5")

    /** 기본 최소 신뢰도 (0~1) */
    const val DEFAULT_MIN_CONFIDENCE = 0.7  // → 5.25점

    // ═════════════════════════════════════════════════
    // 4. 가격 추천 기준
    // ═════════════════════════════════════════════════

    /** 강력매수 임계값 */
    val STRONG_BUY_THRESHOLD = BigDecimal("6.5")

    /** 매수 임계값 */
    val BUY_THRESHOLD = BigDecimal("5.5")

    /** 보유 임계값 */
    val HOLD_THRESHOLD = BigDecimal("4.0")

    /** 매도 임계값 */
    val SELL_THRESHOLD = BigDecimal("2.5")

    // 강력매도: 2.5 미만

    // ═════════════════════════════════════════════════
    // 유틸리티 메서드
    // ═════════════════════════════════════════════════

    /**
     * 가중치 검증 (합계 = 1.0)
     */
    fun validateWeights(): Boolean {
        val sum = WEIGHT_AI + WEIGHT_TECH + WEIGHT_SENTIMENT
        return sum.compareTo(BigDecimal.ONE) == 0
    }

    /**
     * 신뢰도 → Composite Score 변환
     */
    fun confidenceToScore(confidence: Double): BigDecimal {
        return BigDecimal(confidence) * MAX_SCORE
    }

    /**
     * Composite Score → 신뢰도 변환
     */
    fun scoreToConfidence(score: BigDecimal): Double {
        return (score / MAX_SCORE).toDouble()
    }
}
