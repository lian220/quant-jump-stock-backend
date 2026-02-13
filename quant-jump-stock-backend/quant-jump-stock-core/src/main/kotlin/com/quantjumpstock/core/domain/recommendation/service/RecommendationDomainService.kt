package com.quantjumpstock.core.domain.recommendation.service

import com.quantjumpstock.core.domain.model.prediction.PredictionResult
import com.quantjumpstock.core.domain.recommendation.model.*
import java.math.BigDecimal

/**
 * 추천 도메인 서비스
 *
 * 예측 결과를 기반으로 종합적인 추천을 생성하는 핵심 도메인 로직.
 * 점수 계산 + 리스크 평가 + 추천 여부 결정을 통합.
 */
class RecommendationDomainService(
    private val strategyFactory: ScoringStrategyFactory = ScoringStrategyFactory()
) {
    /**
     * 추천 생성
     *
     * @param prediction 예측 결과 (AI + Tech + Sentiment)
     * @param marketContext 시장 컨텍스트 (선택, 기본값: 중립)
     * @return 풍부한 추천 정보
     */
    fun generateRecommendation(
        prediction: PredictionResult,
        marketContext: MarketContext = MarketContext.default()
    ): EnrichedRecommendation {
        // 1. 전략 선택 (시장 상황별)
        val strategy = strategyFactory.selectStrategy(marketContext)

        // 2. 점수 계산 (동적 가중치 적용)
        val score = strategy.calculateScore(
            aiScore = prediction.aiScore,
            techScore = prediction.techScore,
            sentimentScore = prediction.sentimentNormalizedScore,
            context = marketContext
        )

        // 3. 리스크 평가 (간단 버전: 변동성 기반)
        val risk = assessRisk(prediction)

        // 4. 추천 여부 결정 (도메인 규칙)
        val isRecommended = decideRecommendation(score, risk, marketContext)

        // 5. 가격 추천 등급 결정
        val priceRecommendation = determinePriceRecommendation(score, risk)

        // 6. 추천 사유 생성
        val reason = generateReason(score, risk, marketContext, priceRecommendation)

        return EnrichedRecommendation(
            score = score,
            grade = score.toGrade(),
            priceRecommendation = priceRecommendation,
            riskAssessment = risk,
            isRecommended = isRecommended,
            reason = reason,
            strategyName = strategy.name
        )
    }

    /**
     * 리스크 평가 (간단 버전)
     */
    private fun assessRisk(prediction: PredictionResult): RiskAssessment {
        // TODO: 실제 변동성 데이터가 있다면 사용
        // 현재는 기본값 사용
        return RiskAssessment.default()
    }

    /**
     * 추천 여부 결정 (도메인 규칙)
     */
    private fun decideRecommendation(
        score: RecommendationScore,
        risk: RiskAssessment,
        context: MarketContext
    ): Boolean {
        // 규칙 1: 극한 리스크는 무조건 제외
        if (!risk.isAcceptable()) {
            return false
        }

        // 규칙 2: 최소 점수 기준 (시장 상황별 조정)
        val minScore = when {
            context.isBullish() -> BigDecimal("4.5")      // 강세장: 4.5점
            context.isBearish() -> BigDecimal("5.5")      // 약세장: 5.5점 (더 보수적)
            else -> BigDecimal("5.0")                      // 횡보장: 5.0점
        }

        if (!score.meetsMinimum(minScore)) {
            return false
        }

        // 규칙 3: 고위험 종목은 더 높은 점수 요구
        if (risk.isHigh()) {
            val highRiskMinScore = BigDecimal("6.0")
            if (!score.meetsMinimum(highRiskMinScore)) {
                return false
            }
        }

        return true
    }

    /**
     * 가격 추천 등급 결정
     */
    private fun determinePriceRecommendation(
        score: RecommendationScore,
        risk: RiskAssessment
    ): PriceRecommendation {
        // 극한 리스크는 무조건 매도
        if (risk.level == RiskLevel.EXTREME) {
            return PriceRecommendation.STRONG_SELL
        }

        // 점수 기반 추천
        return when {
            score.value >= BigDecimal("6.5") && risk.level == RiskLevel.LOW -> {
                PriceRecommendation.STRONG_BUY  // 고득점 + 저위험
            }
            score.value >= BigDecimal("5.5") -> {
                PriceRecommendation.BUY  // 고득점
            }
            score.value >= BigDecimal("4.0") -> {
                PriceRecommendation.HOLD  // 중간 득점
            }
            score.value >= BigDecimal("2.5") -> {
                PriceRecommendation.SELL  // 저득점
            }
            else -> {
                PriceRecommendation.STRONG_SELL  // 매우 저득점
            }
        }
    }

    /**
     * 추천 사유 생성
     */
    private fun generateReason(
        score: RecommendationScore,
        risk: RiskAssessment,
        context: MarketContext,
        priceRec: PriceRecommendation
    ): String {
        val reasons = mutableListOf<String>()

        // 점수 기반 사유
        reasons.add("Composite Score: ${score.value} (${score.toGrade().displayName})")

        // 시장 상황
        reasons.add("시장: ${context.trend.displayName} (${context.volatility.displayName})")

        // 리스크
        reasons.add("리스크: ${risk.level.displayName}")

        // 추천 결과
        reasons.add("추천: ${priceRec.displayName}")

        return reasons.joinToString(", ")
    }
}

/**
 * 풍부한 추천 정보
 *
 * PredictionResult에 추가할 정보들을 담은 도메인 모델.
 */
data class EnrichedRecommendation(
    val score: RecommendationScore,
    val grade: RecommendationGrade,
    val priceRecommendation: PriceRecommendation,
    val riskAssessment: RiskAssessment,
    val isRecommended: Boolean,
    val reason: String,
    val strategyName: String
)
