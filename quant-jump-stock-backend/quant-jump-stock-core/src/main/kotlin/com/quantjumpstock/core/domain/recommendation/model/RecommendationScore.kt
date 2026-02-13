package com.quantjumpstock.core.domain.recommendation.model

import java.math.BigDecimal

/**
 * 추천 점수 Value Object
 *
 * Composite Score(0.0 ~ 7.5)를 도메인 개념으로 캡슐화.
 * 불변성과 유효성 검증을 보장.
 */
@JvmInline
value class RecommendationScore(val value: BigDecimal) {

    init {
        require(value >= BigDecimal.ZERO && value <= MAX_SCORE) {
            "Score must be between 0 and 7.5, but was $value"
        }
    }

    /**
     * 등급 판정
     */
    fun toGrade(): RecommendationGrade {
        return when {
            value >= THRESHOLD_S -> RecommendationGrade.EXCEPTIONAL
            value >= THRESHOLD_A -> RecommendationGrade.STRONG
            value >= THRESHOLD_B -> RecommendationGrade.MODERATE
            value >= THRESHOLD_C -> RecommendationGrade.WEAK
            else -> RecommendationGrade.INSUFFICIENT
        }
    }

    /**
     * 점수 비교
     */
    fun isHigherThan(other: RecommendationScore): Boolean = value > other.value

    /**
     * 최소 점수 충족 여부
     */
    fun meetsMinimum(minScore: BigDecimal): Boolean = value >= minScore

    companion object {
        // 최대 점수
        val MAX_SCORE = BigDecimal("7.5")

        // 등급 임계값 (하드코딩, 나중에 DB로 이동 가능)
        private val THRESHOLD_S = BigDecimal("6.0")
        private val THRESHOLD_A = BigDecimal("4.5")
        private val THRESHOLD_B = BigDecimal("3.0")
        private val THRESHOLD_C = BigDecimal("1.5")

        /**
         * AI + Tech + Sentiment 점수로부터 Composite Score 계산
         */
        fun calculate(
            aiScore: BigDecimal,
            techScore: BigDecimal,
            sentimentScore: BigDecimal
        ): RecommendationScore {
            // 가중치 (하드코딩, 나중에 DB로 이동 가능)
            val weightAi = BigDecimal("0.3")
            val weightTech = BigDecimal("0.4")
            val weightSentiment = BigDecimal("0.3")

            val compositeScore = (aiScore * weightAi)
                .add(techScore * weightTech)
                .add(sentimentScore * weightSentiment)
                .setScale(2, java.math.RoundingMode.HALF_UP)

            return RecommendationScore(compositeScore)
        }

        /**
         * 0점 (최소값)
         */
        fun zero(): RecommendationScore = RecommendationScore(BigDecimal.ZERO)

        /**
         * 최대 점수
         */
        fun max(): RecommendationScore = RecommendationScore(MAX_SCORE)
    }
}
