package com.quantjumpstock.core.application.news

import com.quantjumpstock.core.domain.news.model.NewsItem
import com.quantjumpstock.core.domain.news.model.ScoredNewsItem
import org.springframework.stereotype.Component

@Component
class NewsScorer(
    private val categoryService: NewsCategoryService
) {

    private val sourceWeights = mapOf(
        "reuters" to 0.15,
        "로이터" to 0.15,
        "블룸버그" to 0.15,
        "파이낸셜타임즈" to 0.15,
        "financial-juice" to 0.10
    )
    private val defaultSourceWeight = 0.05

    fun score(item: NewsItem): ScoredNewsItem {
        val reasons = mutableListOf<String>()
        val categoryWeights = categoryService.getAllCategoryWeights()

        val sourceScore = sourceWeights[item.originalSource] ?: defaultSourceWeight
        if (sourceScore >= 0.15) reasons += "${item.originalSource ?: "SAVE"} (고신뢰)"

        // raw 태그를 정규화 카테고리로 변환 후 가중치 조회
        val resolvedTags = categoryService.resolveTags(item.source.name, item.tags)
        val categoryScore = resolvedTags
            .mapNotNull { categoryWeights[it] }
            .maxOrNull() ?: 0.10
        val topCategory = resolvedTags
            .maxByOrNull { categoryWeights[it] ?: 0.0 }
        if (topCategory != null) reasons += topCategory

        val tickerBonus = when {
            item.tickers.size >= 3 -> 0.15
            item.tickers.size >= 1 -> 0.05
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
            newsItem = item.copy(importanceScore = totalScore, tags = resolvedTags),
            score = totalScore,
            matchedTickers = item.tickers.toSet(),
            reasons = reasons,
            isRumor = isRumor
        )
    }
}
