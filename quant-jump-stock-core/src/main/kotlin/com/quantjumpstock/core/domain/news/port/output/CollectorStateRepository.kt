package com.quantjumpstock.core.domain.news.port.output

import com.quantjumpstock.core.domain.news.model.CollectorState
import com.quantjumpstock.core.domain.news.model.NewsSource
import java.time.LocalDateTime

interface CollectorStateRepository {
    fun getState(source: NewsSource): CollectorState?
    fun getLastFetchedAt(source: NewsSource): LocalDateTime?
    fun getLastFetchedId(source: NewsSource): String?
    fun updateState(source: NewsSource, lastFetchedAt: LocalDateTime, lastFetchedId: String?)
    fun recordError(source: NewsSource, error: String)
    fun recordSuccess(source: NewsSource, responseTimeMs: Long)
}
