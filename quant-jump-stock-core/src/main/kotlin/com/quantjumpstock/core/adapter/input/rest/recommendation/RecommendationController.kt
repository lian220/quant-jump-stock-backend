package com.quantjumpstock.core.adapter.input.rest.recommendation

import com.quantjumpstock.core.application.recommendation.RecommendationService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 추천 시스템 API Controller
 *
 * Composite Score 기반 종목 추천 엔드포인트.
 */
@RestController
@RequestMapping("/api/predictions")
class RecommendationController(
    private val recommendationService: RecommendationService
) {

    /**
     * 매수 신호 조회
     *
     * GET /api/predictions/buy-signals?minConfidence=0.7
     */
    @GetMapping("/buy-signals")
    fun getBuySignals(
        @RequestParam(defaultValue = "0.7") minConfidence: Double
    ) = recommendationService.getBuySignals(minConfidence)
}
