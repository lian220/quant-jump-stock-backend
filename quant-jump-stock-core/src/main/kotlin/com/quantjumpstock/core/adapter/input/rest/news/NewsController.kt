package com.quantjumpstock.core.adapter.input.rest.news

import com.quantjumpstock.core.application.news.NewsListResponse
import com.quantjumpstock.core.application.news.NewsQueryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/news")
@Tag(name = "News", description = "뉴스 조회 API")
class NewsController(
    private val newsQueryService: NewsQueryService
) {

    @GetMapping("/recent")
    @Operation(
        summary = "최신 뉴스 조회",
        description = "중요도 순으로 정렬된 최신 뉴스를 조회합니다."
    )
    fun getRecentNews(
        @Parameter(description = "조회 건수", example = "20")
        @RequestParam(required = false, defaultValue = "20") limit: Int
    ): ResponseEntity<NewsListResponse> {
        val response = newsQueryService.getRecentNews(limit)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/by-tickers")
    @Operation(
        summary = "티커별 뉴스 조회",
        description = "특정 종목 티커와 관련된 뉴스를 조회합니다."
    )
    fun getNewsByTickers(
        @Parameter(description = "티커 목록 (쉼표 구분)", example = "AAPL,MSFT")
        @RequestParam tickers: List<String>,

        @Parameter(description = "조회 건수", example = "20")
        @RequestParam(required = false, defaultValue = "20") limit: Int
    ): ResponseEntity<NewsListResponse> {
        val response = newsQueryService.getNewsByTickers(tickers, limit)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/by-tags")
    @Operation(
        summary = "태그별 뉴스 조회",
        description = "특정 태그와 관련된 뉴스를 조회합니다."
    )
    fun getNewsByTags(
        @Parameter(description = "태그 목록 (쉼표 구분)", example = "경제,연준")
        @RequestParam tags: List<String>,

        @Parameter(description = "조회 건수", example = "20")
        @RequestParam(required = false, defaultValue = "20") limit: Int
    ): ResponseEntity<NewsListResponse> {
        val response = newsQueryService.getNewsByTags(tags, limit)
        return ResponseEntity.ok(response)
    }
}
