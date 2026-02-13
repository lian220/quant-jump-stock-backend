package com.quantjumpstock.core.domain.recommendation.service

import com.quantjumpstock.core.domain.recommendation.model.MarketContext
import com.quantjumpstock.core.domain.recommendation.model.RecommendationScore
import java.math.BigDecimal

/**
 * 점수 계산 전략 인터페이스
 *
 * 시장 상황에 따라 다른 가중치를 적용할 수 있도록 전략 패턴 사용.
 * 현재는 하드코딩, 나중에 DB에서 가중치를 가져올 수 있음.
 */
interface ScoringStrategy {
    /**
     * Composite Score 계산
     */
    fun calculateScore(
        aiScore: BigDecimal,
        techScore: BigDecimal,
        sentimentScore: BigDecimal,
        context: MarketContext
    ): RecommendationScore

    /**
     * 전략 이름
     */
    val name: String
}

/**
 * 표준 점수 계산 전략 (기본값)
 *
 * AI 30%, Tech 40%, Sentiment 30%
 */
class StandardScoringStrategy : ScoringStrategy {
    override fun calculateScore(
        aiScore: BigDecimal,
        techScore: BigDecimal,
        sentimentScore: BigDecimal,
        context: MarketContext
    ): RecommendationScore {
        val weightAi = BigDecimal("0.3")
        val weightTech = BigDecimal("0.4")
        val weightSentiment = BigDecimal("0.3")

        val compositeScore = (aiScore * weightAi)
            .add(techScore * weightTech)
            .add(sentimentScore * weightSentiment)
            .setScale(2, java.math.RoundingMode.HALF_UP)

        return RecommendationScore(compositeScore)
    }

    override val name: String = "표준 전략"
}

/**
 * 강세장 전략
 *
 * AI 예측 가중치 증가 (AI 40%, Tech 35%, Sentiment 25%)
 * 단기 상승 모멘텀을 더 중요하게 평가
 */
class BullMarketStrategy : ScoringStrategy {
    override fun calculateScore(
        aiScore: BigDecimal,
        techScore: BigDecimal,
        sentimentScore: BigDecimal,
        context: MarketContext
    ): RecommendationScore {
        val weightAi = BigDecimal("0.4")        // 증가
        val weightTech = BigDecimal("0.35")     // 감소
        val weightSentiment = BigDecimal("0.25") // 감소

        val compositeScore = (aiScore * weightAi)
            .add(techScore * weightTech)
            .add(sentimentScore * weightSentiment)
            .setScale(2, java.math.RoundingMode.HALF_UP)

        return RecommendationScore(compositeScore)
    }

    override val name: String = "강세장 전략"
}

/**
 * 약세장 전략
 *
 * 기술적 안정성 가중치 증가 (AI 20%, Tech 50%, Sentiment 30%)
 * 기술적 지표를 통한 안정성을 더 중요하게 평가
 */
class BearMarketStrategy : ScoringStrategy {
    override fun calculateScore(
        aiScore: BigDecimal,
        techScore: BigDecimal,
        sentimentScore: BigDecimal,
        context: MarketContext
    ): RecommendationScore {
        val weightAi = BigDecimal("0.2")        // 감소
        val weightTech = BigDecimal("0.5")      // 증가
        val weightSentiment = BigDecimal("0.3") // 유지

        val compositeScore = (aiScore * weightAi)
            .add(techScore * weightTech)
            .add(sentimentScore * weightSentiment)
            .setScale(2, java.math.RoundingMode.HALF_UP)

        return RecommendationScore(compositeScore)
    }

    override val name: String = "약세장 전략"
}

/**
 * 전략 팩토리
 *
 * 시장 컨텍스트에 따라 적절한 전략을 선택
 */
class ScoringStrategyFactory {
    fun selectStrategy(context: MarketContext): ScoringStrategy {
        return when {
            context.isBullish() -> BullMarketStrategy()
            context.isBearish() -> BearMarketStrategy()
            else -> StandardScoringStrategy()
        }
    }
}
