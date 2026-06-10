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
    val priceRecommendation: String? = null,    // 가격 추천 등급 (강력매수/매수/보유/매도)

    // PR 2 (2026-05-21): ScoringPolicy 라벨 SSoT + AI 예측 출처 추적
    val recommendationLabel: String? = null,    // STRONG/RECOMMEND/WATCH/NONE — ScoringPolicy 라벨
    val effectivePredictionDate: LocalDate? = null,  // AI 예측 데이터 실제 사용 날짜 (analysis_date 와 다르면 fallback)

    // PR 3a (2026-05-22): 정책 차단 사유 + 비차단 경고 (PR 3b 부터 채워짐)
    val vetoReasons: List<String> = emptyList(),   // 예: ["ai_negative", "ai_source_disagreement"]
    val warnings: List<String> = emptyList(),      // 예: ["vix_macro_caution"]

    // ADR 0006 Phase 2 (2026-06-07): XAI 데이터 브릿지 (Python 산출값 pass-through)
    // axisContributions: 축별 0~100 기여 점수 (w·normalized·100), XAI 막대 표시용
    val axisContributions: Map<String, BigDecimal> = emptyMap(),
    // scoreCoverage: present 축 원본 weight 합 (0~1). 결측 재정규화 후 추천 신뢰도 설명용.
    val scoreCoverage: BigDecimal = BigDecimal.ONE
) {
    companion object {
        /**
         * 등급 판정 — **fallback only**.
         *
         * ⚠️ SSoT = scoring_spec.yaml.grades (S82/A77/B68/C58/D0, 운영 분포 재보정값). 정상 경로는
         * Python 이 산출해 저장한 composite_grade 를 그대로 사용한다(재계산 금지, ADR 0006 §2.6/§2.8).
         * 이 함수는 grade 가 누락된 레거시/예외 row 용 fallback 일 뿐이다.
         */
        fun determineGrade(compositeScore: BigDecimal): CompositeGrade {
            return when {
                compositeScore >= BigDecimal("82") -> CompositeGrade.S
                compositeScore >= BigDecimal("77") -> CompositeGrade.A
                compositeScore >= BigDecimal("68") -> CompositeGrade.B
                compositeScore >= BigDecimal("58") -> CompositeGrade.C
                else -> CompositeGrade.D
            }
        }
    }
}

/**
 * Composite Score 등급 (0~100 단일 스케일).
 *
 * ⚠️ 임계값 SSoT = scoring_spec.yaml.grades. range 문자열은 표시용 참고이며,
 * 점수→등급 판정은 Python 책임이다(Kotlin 재계산 금지, ADR 0006 §2.6).
 */
enum class CompositeGrade(
    val range: String,
    val description: String,
    val color: String
) {
    S("82 ~ 100", "매우 강한 매수 신호", "emerald"),
    A("77 ~ 81", "강한 매수 신호", "cyan"),
    B("68 ~ 76", "보통 매수 신호", "yellow"),
    C("58 ~ 67", "약한 매수 신호", "orange"),
    D("0 ~ 57", "매수 신호 없음", "red");

    companion object {
        fun fromString(value: String): CompositeGrade {
            return entries.find { it.name == value.uppercase() }
                ?: throw IllegalArgumentException("Invalid grade: $value")
        }
    }
}
