package com.quantjumpstock.core.application.news

import com.quantjumpstock.core.domain.news.port.output.NewsRepository
import org.springframework.stereotype.Service

@Service
class NewsQueryService(
    private val newsRepository: NewsRepository
) {

    fun getRecentNews(limit: Int): NewsListResponse {
        val items = newsRepository.findRecent(limit)
        val dtos = items.map { NewsDto.from(it) }
        return NewsListResponse(news = dtos, total = dtos.size)
    }

    fun getNewsByTickers(tickers: List<String>, limit: Int): NewsListResponse {
        val items = newsRepository.findByTickers(tickers, limit)
        val dtos = items.map { NewsDto.from(it) }
        return NewsListResponse(news = dtos, total = dtos.size)
    }

    fun getNewsByTags(tags: List<String>, limit: Int): NewsListResponse {
        val items = newsRepository.findByTags(tags, limit)
        val dtos = items.map { NewsDto.from(it) }
        return NewsListResponse(news = dtos, total = dtos.size)
    }
}
