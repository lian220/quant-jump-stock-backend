package com.quantjumpstock.core.domain.news.model

/**
 * 도메인 레벨 페이지네이션 요청 (Spring Pageable 의존 제거)
 */
data class NewsPageRequest(
    val page: Int,
    val size: Int,
    val sortField: String = "created_at",
    val sortDirection: SortDir = SortDir.DESC
) {
    enum class SortDir { ASC, DESC }
}
