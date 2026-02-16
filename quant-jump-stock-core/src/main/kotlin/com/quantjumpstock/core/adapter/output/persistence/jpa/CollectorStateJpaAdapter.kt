package com.quantjumpstock.core.adapter.output.persistence.jpa

import com.quantjumpstock.core.domain.news.model.CollectorState
import com.quantjumpstock.core.domain.news.model.NewsSource
import com.quantjumpstock.core.domain.news.port.output.CollectorStateRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
@Transactional
class CollectorStateJpaAdapter(
    private val repository: CollectorStateJpaRepository
) : CollectorStateRepository {

    @Transactional(readOnly = true)
    override fun getState(source: NewsSource): CollectorState? {
        return repository.findBySource(source.name)?.toDomain()
    }

    @Transactional(readOnly = true)
    override fun getLastFetchedAt(source: NewsSource): LocalDateTime? {
        return repository.findBySource(source.name)?.lastFetchedAt
    }

    @Transactional(readOnly = true)
    override fun getLastFetchedId(source: NewsSource): String? {
        return repository.findBySource(source.name)?.lastFetchedId
    }

    override fun updateState(source: NewsSource, lastFetchedAt: LocalDateTime, lastFetchedId: String?) {
        val entity = getOrCreate(source)
        entity.lastFetchedAt = lastFetchedAt
        entity.lastFetchedId = lastFetchedId
        entity.fetchCount += 1
        entity.consecutiveErrors = 0
        repository.save(entity)
    }

    override fun recordError(source: NewsSource, error: String) {
        val entity = getOrCreate(source)
        entity.consecutiveErrors += 1
        entity.totalErrors += 1
        entity.lastError = error.take(500)
        entity.lastErrorAt = LocalDateTime.now()
        repository.save(entity)
    }

    override fun recordSuccess(source: NewsSource, responseTimeMs: Long) {
        val entity = getOrCreate(source)
        entity.consecutiveErrors = 0
        entity.lastResponseTimeMs = responseTimeMs
        repository.save(entity)
    }

    private fun getOrCreate(source: NewsSource): CollectorStateEntity {
        return repository.findBySource(source.name)
            ?: repository.save(CollectorStateEntity(source = source.name))
    }
}
