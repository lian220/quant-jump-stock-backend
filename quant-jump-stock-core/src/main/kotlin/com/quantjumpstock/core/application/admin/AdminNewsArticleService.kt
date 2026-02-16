package com.quantjumpstock.core.application.admin

import com.quantjumpstock.core.domain.news.model.NewsItem
import com.quantjumpstock.core.domain.news.model.NewsSource
import com.quantjumpstock.core.domain.news.port.output.NewsRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import kotlin.math.ceil

@Service
class AdminNewsArticleService(
    private val newsRepository: NewsRepository
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /** 기사 목록 조회 (페이지네이션 + 필터) */
    fun getArticles(
        page: Int,
        size: Int,
        source: String?,
        tag: String?,
        ticker: String?,
        dateFrom: LocalDateTime?,
        dateTo: LocalDateTime?,
        includeHidden: Boolean,
        sortBy: String,
        sortDirection: String
    ): AdminNewsArticleListResponse {
        val direction = if (sortDirection.equals("asc", ignoreCase = true)) Sort.Direction.ASC else Sort.Direction.DESC
        val sortField = when (sortBy) {
            "importanceScore" -> "importance_score"
            "viewCount" -> "view_count"
            "createdAt" -> "created_at"
            else -> "created_at"
        }
        val pageable = PageRequest.of(page, size, Sort.by(direction, sortField))

        val (items, total) = newsRepository.findAllPaginated(
            source = source,
            tag = tag,
            ticker = ticker,
            dateFrom = dateFrom,
            dateTo = dateTo,
            includeHidden = includeHidden,
            pageable = pageable
        )

        return AdminNewsArticleListResponse(
            articles = items.map { it.toAdminDto() },
            total = total,
            page = page,
            size = size,
            totalPages = ceil(total.toDouble() / size).toInt()
        )
    }

    /** 기사 상세 조회 */
    fun getArticleById(id: String): AdminNewsArticle {
        val item = newsRepository.findById(id)
            ?: throw IllegalArgumentException("기사를 찾을 수 없습니다: $id")
        return item.toAdminDto()
    }

    /** 기사 통계 */
    fun getStats(): AdminNewsArticleStatsResponse {
        val total = newsRepository.countAll()
        val hidden = newsRepository.countHidden()
        val avgImportance = newsRepository.averageImportance()

        return AdminNewsArticleStatsResponse(
            total = total,
            active = total - hidden,
            hidden = hidden,
            avgImportance = Math.round(avgImportance * 100.0) / 100.0
        )
    }

    /** 수동 기사 생성 */
    fun createArticle(request: CreateNewsArticleRequest): AdminNewsArticle {
        val now = LocalDateTime.now()
        val item = NewsItem(
            externalId = "manual_${now.toLocalDate()}_${System.currentTimeMillis()}",
            source = NewsSource.MANUAL,
            originalSource = null,
            titleKo = request.titleKo,
            titleEn = request.titleEn,
            contentKo = request.contentKo,
            summaryKo = request.summaryKo,
            tags = request.tags,
            tickers = request.tickers,
            importanceScore = request.importanceScore,
            isHeadlineOnly = false,
            isHidden = false,
            viewCount = 0,
            sourceUrl = request.sourceUrl,
            sourceCreatedAt = now,
            createdAt = now,
            extra = emptyMap()
        )
        val saved = newsRepository.save(item)
        logger.info("수동 뉴스 기사 생성: {}", saved.id)
        return saved.toAdminDto()
    }

    /** 기사 메타 수정 */
    fun updateArticle(id: String, request: UpdateNewsArticleRequest): AdminNewsArticle {
        val existing = newsRepository.findById(id)
            ?: throw IllegalArgumentException("기사를 찾을 수 없습니다: $id")

        val updates = mutableMapOf<String, Any?>()
        request.titleKo?.let { updates["title_ko"] = it }
        request.titleEn?.let { updates["title_en"] = it }
        request.summaryKo?.let { updates["summary_ko"] = it }
        request.tags?.let { updates["tags"] = it }
        request.tickers?.let { updates["tickers"] = it }
        request.importanceScore?.let { updates["importance_score"] = it }

        if (updates.isNotEmpty()) {
            newsRepository.updateMetadata(id, updates)
            logger.info("뉴스 기사 수정: {}", id)
        }

        return (newsRepository.findById(id)
            ?: throw IllegalArgumentException("기사를 찾을 수 없습니다: $id")).toAdminDto()
    }

    /** 기사 숨김 (soft delete) */
    fun hideArticle(id: String) {
        newsRepository.findById(id)
            ?: throw IllegalArgumentException("기사를 찾을 수 없습니다: $id")
        newsRepository.softDelete(id)
        logger.info("뉴스 기사 숨김: {}", id)
    }

    /** 기사 숨김 해제 */
    fun unhideArticle(id: String) {
        newsRepository.findById(id)
            ?: throw IllegalArgumentException("기사를 찾을 수 없습니다: $id")
        newsRepository.restore(id)
        logger.info("뉴스 기사 숨김 해제: {}", id)
    }
}

private fun NewsItem.toAdminDto() = AdminNewsArticle(
    id = id ?: "",
    externalId = externalId,
    source = source.name,
    originalSource = originalSource,
    titleKo = titleKo,
    titleEn = titleEn,
    contentKo = contentKo,
    summaryKo = summaryKo,
    tags = tags,
    tickers = tickers,
    importanceScore = importanceScore,
    isHeadlineOnly = isHeadlineOnly,
    viewCount = viewCount,
    sourceUrl = sourceUrl,
    isHidden = isHidden,
    sourceCreatedAt = sourceCreatedAt?.toString(),
    createdAt = createdAt?.toString()
)
