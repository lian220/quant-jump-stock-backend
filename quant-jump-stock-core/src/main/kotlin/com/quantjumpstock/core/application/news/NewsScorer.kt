package com.quantjumpstock.core.application.news

import com.quantjumpstock.core.domain.news.model.NewsItem
import com.quantjumpstock.core.domain.news.model.ScoredNewsItem
import org.springframework.stereotype.Component

@Component
class NewsScorer {

    private val sourceWeights = mapOf(
        "reuters" to 0.15,
        "블룸버그" to 0.15,
        "파이낸셜타임즈" to 0.15,
        "financial-juice" to 0.10,
        "로이터" to 0.10
    )
    private val defaultSourceWeight = 0.05

    private val categoryWeights = mapOf(
        "속보" to 0.40,
        "경제지표" to 0.35,
        "연준" to 0.35,
        "투자 의견" to 0.30,
        "분석" to 0.25,
        "에너지" to 0.20,
        "종합" to 0.15,
        "정보" to 0.15,
        "암호화폐" to 0.10,
        "일정" to 0.10
    )

    fun score(item: NewsItem): ScoredNewsItem {
        val reasons = mutableListOf<String>()

        val sourceScore = sourceWeights[item.originalSource] ?: defaultSourceWeight
        if (sourceScore >= 0.15) reasons += "${item.originalSource ?: "SAVE"} (고신뢰)"

        val categoryScore = item.tags
            .mapNotNull { categoryWeights[it] }
            .maxOrNull() ?: 0.10
        val topCategory = item.tags
            .maxByOrNull { categoryWeights[it] ?: 0.0 }
        if (topCategory != null) reasons += topCategory

        val tickerBonus = when {
            item.tickers.size >= 3 -> 0.05
            item.tickers.size >= 1 -> 0.15
            else -> 0.0
        }
        if (item.tickers.isNotEmpty()) reasons += item.tickers.joinToString()

        val depthBonus = if (!item.isHeadlineOnly &&
            (item.contentKo?.length ?: 0) > 100) 0.10 else 0.0
        if (depthBonus > 0) reasons += "본문 포함"

        val isRumor = item.titleKo.startsWith("(카더라)")
        if (isRumor) reasons += "미확인"

        val totalScore = (sourceScore + categoryScore + tickerBonus + depthBonus)
            .coerceAtMost(1.0)

        return ScoredNewsItem(
            newsItem = item.copy(importanceScore = totalScore),
            score = totalScore,
            matchedTickers = item.tickers.toSet(),
            reasons = reasons,
            isRumor = isRumor
        )
    }
}
