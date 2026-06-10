package com.quantjumpstock.core.adapter.output.persistence.jpa.prediction

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.quantjumpstock.core.domain.model.prediction.CompositeGrade
import com.quantjumpstock.core.domain.model.prediction.PredictionResult
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 통합 예측 결과 JPA Entity
 *
 * MongoDB 분석 결과를 통합한 PostgreSQL 엔티티.
 */
@Entity
@Table(
    name = "prediction_results",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["ticker", "analysis_date"])
    ],
    indexes = [
        Index(name = "idx_prediction_results_date", columnList = "analysis_date DESC"),
        Index(name = "idx_prediction_results_composite", columnList = "composite_score DESC"),
        Index(name = "idx_prediction_results_recommended", columnList = "is_recommended, composite_score DESC")
    ]
)
class PredictionResultEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // 종목 정보
    @Column(nullable = false, length = 20)
    val ticker: String,

    @Column(name = "stock_name", nullable = false)
    val stockName: String,

    @Column(name = "analysis_date", nullable = false)
    val analysisDate: LocalDate,

    // AI 예측 점수
    @Column(name = "ai_predicted_price", precision = 10, scale = 2)
    val aiPredictedPrice: BigDecimal? = null,

    @Column(name = "ai_rise_probability", precision = 5, scale = 4)
    val aiRiseProbability: BigDecimal? = null,

    @Column(name = "ai_score", precision = 5, scale = 2, nullable = false)
    val aiScore: BigDecimal = BigDecimal.ZERO,

    // 감정 분석 점수
    @Column(name = "sentiment_score", precision = 5, scale = 4)
    val sentimentScore: BigDecimal? = null,

    @Column(name = "sentiment_news_count")
    val sentimentNewsCount: Int? = null,

    @Column(name = "sentiment_normalized_score", precision = 5, scale = 2, nullable = false)
    val sentimentNormalizedScore: BigDecimal = BigDecimal.ZERO,

    // 기술적 분석 점수
    @Column(name = "tech_sma20", precision = 10, scale = 2)
    val techSma20: BigDecimal? = null,

    @Column(name = "tech_sma50", precision = 10, scale = 2)
    val techSma50: BigDecimal? = null,

    @Column(name = "tech_rsi", precision = 5, scale = 2)
    val techRsi: BigDecimal? = null,

    @Column(name = "tech_macd", precision = 10, scale = 2)
    val techMacd: BigDecimal? = null,

    @Column(name = "tech_signal", precision = 10, scale = 2)
    val techSignal: BigDecimal? = null,

    @Column(name = "tech_golden_cross", nullable = false)
    val techGoldenCross: Boolean = false,

    @Column(name = "tech_macd_buy_signal", nullable = false)
    val techMacdBuySignal: Boolean = false,

    @Column(name = "tech_score", precision = 5, scale = 2, nullable = false)
    val techScore: BigDecimal = BigDecimal.ZERO,

    @Column(name = "tech_signals_count", nullable = false)
    val techSignalsCount: Int = 0,

    // Composite Score
    @Column(name = "composite_score", precision = 6, scale = 2, nullable = false)
    val compositeScore: BigDecimal,

    @Column(name = "composite_grade", length = 10)
    val compositeGrade: String,

    // 추천 여부
    @Column(name = "is_recommended", nullable = false)
    val isRecommended: Boolean = false,

    @Column(name = "recommendation_reason", columnDefinition = "TEXT")
    val recommendationReason: String? = null,

    // 가격 메트릭 (Phase 6.5 추가)
    @Column(name = "current_price", precision = 10, scale = 2)
    val currentPrice: BigDecimal? = null,

    @Column(name = "target_price", precision = 10, scale = 2)
    val targetPrice: BigDecimal? = null,

    @Column(name = "upside_percent", precision = 6, scale = 2)
    val upsidePercent: BigDecimal? = null,

    @Column(name = "price_recommendation", length = 20)
    val priceRecommendation: String? = null,

    // PR 2 (2026-05-21): ScoringPolicy 라벨 SSoT + AI 예측 출처 날짜
    @Column(name = "recommendation_label", length = 20)
    val recommendationLabel: String? = null,

    @Column(name = "effective_prediction_date")
    val effectivePredictionDate: LocalDate? = null,

    // PR 3a (2026-05-22): 정책 차단 사유 + 비차단 경고 — PG TEXT[] 매핑
    // PR 3b 부터 채워짐 (negative AI veto 등). PR 3a 는 schema 만 준비.
    @Column(name = "veto_reasons", columnDefinition = "text[]")
    val vetoReasons: Array<String>? = null,

    @Column(name = "warnings", columnDefinition = "text[]")
    val warnings: Array<String>? = null,

    // ADR 0006 Phase 2 (2026-06-07): XAI 데이터 브릿지 — JSONB pass-through
    // axisContributions: 축별 0~100 기여 dict (Python 산출). JSON 문자열로 보관(JSONB 매핑).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "axis_contributions", columnDefinition = "jsonb")
    val axisContributions: String = "{}",

    @Column(name = "score_coverage", precision = 4, scale = 3)
    val scoreCoverage: BigDecimal = BigDecimal.ONE,

    // 메타데이터
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * Entity → Domain 변환
     */
    fun toDomain(): PredictionResult {
        return PredictionResult(
            ticker = ticker,
            stockName = stockName,
            analysisDate = analysisDate,
            aiPredictedPrice = aiPredictedPrice,
            aiRiseProbability = aiRiseProbability,
            aiScore = aiScore,
            sentimentScore = sentimentScore,
            sentimentNewsCount = sentimentNewsCount,
            sentimentNormalizedScore = sentimentNormalizedScore,
            techSma20 = techSma20,
            techSma50 = techSma50,
            techRsi = techRsi,
            techMacd = techMacd,
            techSignal = techSignal,
            techGoldenCross = techGoldenCross,
            techMacdBuySignal = techMacdBuySignal,
            techScore = techScore,
            techSignalsCount = techSignalsCount,
            compositeScore = compositeScore,
            compositeGrade = CompositeGrade.fromString(compositeGrade),
            isRecommended = isRecommended,
            recommendationReason = recommendationReason,
            currentPrice = currentPrice,
            targetPrice = targetPrice,
            upsidePercent = upsidePercent,
            priceRecommendation = priceRecommendation,  // String 그대로 전달
            recommendationLabel = recommendationLabel,
            effectivePredictionDate = effectivePredictionDate,
            vetoReasons = vetoReasons?.toList() ?: emptyList(),
            warnings = warnings?.toList() ?: emptyList(),
            axisContributions = parseAxisContributions(axisContributions),
            scoreCoverage = scoreCoverage
        )
    }

    companion object {
        private val objectMapper: ObjectMapper = jacksonObjectMapper()

        /** JSONB 문자열 → Map<String, BigDecimal>. 파싱 실패/빈 값이면 emptyMap. */
        private fun parseAxisContributions(json: String?): Map<String, BigDecimal> {
            if (json.isNullOrBlank() || json == "{}") return emptyMap()
            return try {
                @Suppress("UNCHECKED_CAST")
                val raw = objectMapper.readValue(json, Map::class.java) as Map<String, Any>
                raw.mapValues { (_, v) -> BigDecimal(v.toString()) }
            } catch (e: Exception) {
                emptyMap()
            }
        }

        /** Map<String, BigDecimal> → JSONB 문자열. 빈 맵이면 "{}". */
        private fun writeAxisContributions(map: Map<String, BigDecimal>): String {
            if (map.isEmpty()) return "{}"
            return objectMapper.writeValueAsString(map.mapValues { it.value.toDouble() })
        }

        /**
         * Domain → Entity 변환
         */
        fun fromDomain(domain: PredictionResult): PredictionResultEntity {
            return PredictionResultEntity(
                ticker = domain.ticker,
                stockName = domain.stockName,
                analysisDate = domain.analysisDate,
                aiPredictedPrice = domain.aiPredictedPrice,
                aiRiseProbability = domain.aiRiseProbability,
                aiScore = domain.aiScore,
                sentimentScore = domain.sentimentScore,
                sentimentNewsCount = domain.sentimentNewsCount,
                sentimentNormalizedScore = domain.sentimentNormalizedScore,
                techSma20 = domain.techSma20,
                techSma50 = domain.techSma50,
                techRsi = domain.techRsi,
                techMacd = domain.techMacd,
                techSignal = domain.techSignal,
                techGoldenCross = domain.techGoldenCross,
                techMacdBuySignal = domain.techMacdBuySignal,
                techScore = domain.techScore,
                techSignalsCount = domain.techSignalsCount,
                compositeScore = domain.compositeScore,
                compositeGrade = domain.compositeGrade.name,
                isRecommended = domain.isRecommended,
                recommendationReason = domain.recommendationReason,
                currentPrice = domain.currentPrice,
                targetPrice = domain.targetPrice,
                upsidePercent = domain.upsidePercent,
                priceRecommendation = domain.priceRecommendation,  // String 그대로
                recommendationLabel = domain.recommendationLabel,
                effectivePredictionDate = domain.effectivePredictionDate,
                vetoReasons = domain.vetoReasons.takeIf { it.isNotEmpty() }?.toTypedArray(),
                warnings = domain.warnings.takeIf { it.isNotEmpty() }?.toTypedArray(),
                axisContributions = writeAxisContributions(domain.axisContributions),
                scoreCoverage = domain.scoreCoverage
            )
        }
    }
}
