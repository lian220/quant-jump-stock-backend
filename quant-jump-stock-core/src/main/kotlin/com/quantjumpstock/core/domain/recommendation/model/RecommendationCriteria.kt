package com.quantjumpstock.core.domain.recommendation.model

import java.math.BigDecimal

/**
 * 종목 추천 기준 상수
 *
 * ⭐ 추천 관련 임계값을 한 곳에 모아 관리 ⭐
 *
 * ## SSoT 경고 (ADR 0006 §2.8)
 * 점수 산식·정규화·grade 임계값의 단일 원천(SSoT)은 Python `scoring_spec.yaml` 이다.
 * Kotlin Core 는 **점수를 재계산/재정규화하지 않는다.** PostgreSQL `prediction_results` 에
 * 저장된 0~100 composite, grade, axis_contributions, is_recommended 를 그대로 통과(pass-through)
 * 시킨다. 아래 값들은 필터링·하위호환을 위한 **잠정값**이며, 점수 계산에 쓰지 않는다.
 *
 * ## 점수 체계 개요 (ADR 0006 — 0~100 단일 스케일)
 * ```
 * composite_100 = (w_tech·(tech/3.5) + w_ai·(ai/10) + w_sent·(sent/10)) × 100
 * weight: tech 0.5 / ai 0.3 / sentiment 0.2 (합=1.0)
 * ```
 * 위 산식은 Python 책임. Kotlin 은 결과만 표시한다.
 */
object RecommendationCriteria {

    // ═════════════════════════════════════════════════
    // 1. 점수 계산 가중치 (참고용 — Kotlin 은 점수 계산 안 함)
    //    SSoT = scoring_spec.yaml. ADR 0006 §2.2 기준값.
    // ═════════════════════════════════════════════════

    /** AI 예측 가중치 (30%) — 참고용. 실제 계산은 Python. */
    val WEIGHT_AI = BigDecimal("0.3")

    /** 기술적 분석 가중치 (50%) — 참고용. 실제 계산은 Python. */
    val WEIGHT_TECH = BigDecimal("0.5")

    /** 감정 분석 가중치 (20%) — 참고용. 실제 계산은 Python. */
    val WEIGHT_SENTIMENT = BigDecimal("0.2")

    // ═════════════════════════════════════════════════
    // 2. 추천 기준 (0~100, 잠정값 — scoring_spec.yaml SSoT)
    // ═════════════════════════════════════════════════

    /**
     * 최소 추천 점수 (0~100).
     * scoring_spec.yaml `recommendation_filter.min_composite_score` 의 복사본 — grade B(68) 경계 정합
     * (2026-06-10 재보정). yaml 변경 시 함께 갱신해야 하며, ScoringSpecParityTest 가 드리프트를 차단한다.
     */
    val MIN_RECOMMEND_SCORE = BigDecimal("68.0")

    /** 최대 점수 (0~100 단일 스케일). */
    val MAX_SCORE = BigDecimal("100")

    /** 기본 최소 신뢰도 (0~1). minConfidence × 100 으로 0~100 필터 변환. */
    const val DEFAULT_MIN_CONFIDENCE = 0.68  // → 68점 (grade B 경계, MIN_RECOMMEND_SCORE 와 정합)

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
     * 신뢰도(0~1) → Composite Score(0~100) 변환
     */
    fun confidenceToScore(confidence: Double): BigDecimal {
        return BigDecimal(confidence) * MAX_SCORE
    }

    /**
     * Composite Score(0~100) → 신뢰도(0~1) 변환
     */
    fun scoreToConfidence(score: BigDecimal): Double {
        return (score / MAX_SCORE).toDouble()
    }
}
