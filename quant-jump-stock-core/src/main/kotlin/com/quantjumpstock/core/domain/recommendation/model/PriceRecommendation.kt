package com.quantjumpstock.core.domain.recommendation.model

/**
 * 가격 추천 등급
 *
 * 기존 String("강력매수", "매수", ...)을 타입 안전한 Enum으로 대체.
 * 컴파일 타임에 오타 검증 가능.
 */
enum class PriceRecommendation(
    val action: TradingAction,
    val displayName: String,
    val description: String
) {
    STRONG_BUY(
        action = TradingAction.BUY,
        displayName = "강력매수",
        description = "매우 높은 상승 확률, 적극 매수 권장"
    ),
    BUY(
        action = TradingAction.BUY,
        displayName = "매수",
        description = "상승 가능성 높음, 매수 권장"
    ),
    HOLD(
        action = TradingAction.HOLD,
        displayName = "보유",
        description = "현재 가격 적정, 보유 권장"
    ),
    SELL(
        action = TradingAction.SELL,
        displayName = "매도",
        description = "하락 가능성 있음, 매도 고려"
    ),
    STRONG_SELL(
        action = TradingAction.SELL,
        displayName = "강력매도",
        description = "높은 하락 리스크, 적극 매도 권장"
    );

    /**
     * 매수 신호 여부
     */
    fun isBuySignal(): Boolean = action == TradingAction.BUY

    /**
     * 매도 신호 여부
     */
    fun isSellSignal(): Boolean = action == TradingAction.SELL

    companion object {
        /**
         * String → Enum 변환 (기존 데이터 호환성)
         */
        fun fromString(value: String): PriceRecommendation? {
            return when (value) {
                "강력매수", "STRONG_BUY" -> STRONG_BUY
                "매수", "BUY" -> BUY
                "보유", "HOLD" -> HOLD
                "매도", "SELL" -> SELL
                "강력매도", "STRONG_SELL" -> STRONG_SELL
                else -> null
            }
        }
    }
}

/**
 * 트레이딩 액션
 */
enum class TradingAction {
    BUY,    // 매수
    HOLD,   // 보유
    SELL    // 매도
}
