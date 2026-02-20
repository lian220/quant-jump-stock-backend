package com.quantjumpstock.core.application.marketplace

import io.swagger.v3.oas.annotations.Parameter
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import java.math.BigDecimal

/**
 * 공개 전략 목록 조회 요청
 */
data class StrategyListRequest(
    @Parameter(description = "카테고리 코드 필터 (VALUE, MOMENTUM, ASSET_ALLOCATION, QUANT_COMPOSITE, SEASONAL, CUSTOM, ML_PREDICTION)")
    val categoryCode: String? = null,

    @Parameter(description = "최소 CAGR (%)")
    val minCagr: BigDecimal? = null,

    @Parameter(description = "최대 MDD (%)")
    val maxMdd: BigDecimal? = null,

    @Parameter(description = "정렬 기준 (subscribers, cagr, sharpe, recent)", example = "subscribers")
    val sortBy: String? = "subscribers",

    @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
    val page: Int = 0,

    @Parameter(description = "페이지 크기", example = "20")
    val size: Int = 20
) {
    fun cacheKey(): String = "${categoryCode}:${minCagr}:${maxMdd}:${sortBy}:${page}:${size}"

    fun toPageable(): Pageable {
        val sort = when (sortBy?.lowercase()) {
            "subscribers" -> Sort.by(Sort.Direction.DESC, "subscriberCount")
            "cagr" -> Sort.unsorted() // 백테스트 결과에서 정렬
            "sharpe" -> Sort.unsorted() // 백테스트 결과에서 정렬
            "recent" -> Sort.by(Sort.Direction.DESC, "createdAt")
            else -> Sort.by(Sort.Direction.DESC, "subscriberCount")
        }
        return PageRequest.of(page, size, sort)
    }
}
