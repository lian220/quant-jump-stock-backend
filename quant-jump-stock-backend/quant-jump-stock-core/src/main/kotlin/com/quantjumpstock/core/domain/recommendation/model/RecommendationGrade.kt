package com.quantjumpstock.core.domain.recommendation.model

/**
 * 추천 등급
 *
 * 기존 CompositeGrade(S, A, B, C, D)를 더 명확한 영문으로 대체.
 */
enum class RecommendationGrade(
    val displayName: String,
    val description: String,
    val color: String
) {
    EXCEPTIONAL(
        displayName = "S",
        description = "매우 강한 매수 신호",
        color = "emerald"
    ),
    STRONG(
        displayName = "A",
        description = "강한 매수 신호",
        color = "cyan"
    ),
    MODERATE(
        displayName = "B",
        description = "보통 매수 신호",
        color = "yellow"
    ),
    WEAK(
        displayName = "C",
        description = "약한 매수 신호",
        color = "orange"
    ),
    INSUFFICIENT(
        displayName = "D",
        description = "매수 신호 없음",
        color = "red"
    );

    companion object {
        /**
         * 기존 CompositeGrade와 호환성
         */
        fun fromCompositeGrade(value: String): RecommendationGrade {
            return when (value.uppercase()) {
                "S" -> EXCEPTIONAL
                "A" -> STRONG
                "B" -> MODERATE
                "C" -> WEAK
                "D" -> INSUFFICIENT
                else -> throw IllegalArgumentException("Invalid grade: $value")
            }
        }
    }
}
