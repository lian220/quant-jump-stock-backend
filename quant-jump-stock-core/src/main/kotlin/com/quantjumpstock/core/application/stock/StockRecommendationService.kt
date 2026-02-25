package com.quantjumpstock.core.application.stock

import com.quantjumpstock.core.domain.port.output.StockRecommendationRepository
import com.quantjumpstock.core.domain.port.output.StockAnalysisRepository
import com.quantjumpstock.core.domain.port.output.SentimentAnalysisRepository
import java.time.LocalDate
import org.springframework.stereotype.Service

@Service
class StockRecommendationService(
        private val recommendationRepository: StockRecommendationRepository,
        private val analysisRepository: StockAnalysisRepository,
        private val sentimentRepository: SentimentAnalysisRepository
) {

    fun getAiRecommendations(): List<Map<String, Any>> {
        val analysis =
                analysisRepository
                        .findAll()
                        .filter {
                            val accuracy = it.metrics?.accuracy ?: 0.0
                            val riseProb = it.predictions?.riseProbability ?: 0.0
                            accuracy >= 0.8 && riseProb >= 0.03
                        }
                        .sortedByDescending { it.predictions?.riseProbability ?: 0.0 }

        return analysis.map {
            mapOf(
                    "ticker" to it.ticker,
                    "accuracy" to (it.metrics?.accuracy ?: 0.0),
                    "rise_probability" to (it.predictions?.riseProbability ?: 0.0),
                    "predicted_price" to (it.predictions?.predictedFuturePrice ?: 0.0),
                    "recommendation" to (it.recommendation ?: ""),
                    "analysis" to (it.analysis ?: "")
            )
        }
    }

    fun getRecommendationsWithSentiment(): List<Map<String, Any>> {
        val aiRecs = getAiRecommendations()
        // 분석 데이터는 전날 날짜로 저장됨
        val analysisDate = LocalDate.now().minusDays(1).toString()

        return aiRecs.mapNotNull { rec ->
            val ticker = rec["ticker"] as String
            val sentiment = sentimentRepository.findByTickerAndDate(ticker, analysisDate)
            if (sentiment != null && sentiment.averageSentimentScore >= 0.15) {
                rec + mapOf("sentiment_score" to sentiment.averageSentimentScore)
            } else null
        }
    }

    fun getCombinedRecommendations(): List<Map<String, Any>> {
        val withSentiment = getRecommendationsWithSentiment()
        // 분석 데이터는 전날 날짜로 저장됨
        val analysisDate = LocalDate.now().minusDays(1).toString()

        return withSentiment.mapNotNull { rec ->
            val ticker = rec["ticker"] as String
            val tech = recommendationRepository.findByTickerAndDate(ticker, analysisDate)
            if (tech != null && tech.isRecommended) {
                val result = rec.toMutableMap()
                tech.technicalIndicators?.let { result["technical_indicators"] = it }
                tech.compositeScore?.let { result["composite_score"] = it }
                tech.currentPrice?.let { result["current_price"] = it }
                result.toMap()
            } else null
        }
    }
}
