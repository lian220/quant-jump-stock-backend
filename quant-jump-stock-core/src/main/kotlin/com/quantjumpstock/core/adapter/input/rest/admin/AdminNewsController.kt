package com.quantjumpstock.core.adapter.input.rest.admin

import com.quantjumpstock.core.domain.news.port.input.NewsCollectionUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.annotation.Async
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/news")
@Tag(name = "Admin News", description = "관리자용 뉴스 관리 API")
@PreAuthorize("hasRole('ADMIN')")
class AdminNewsController(
    private val asyncEnrichmentRunner: AsyncEnrichmentRunner
) {

    @PostMapping("/enrich")
    @Operation(
        summary = "기존 기사 본문 보강 (비동기)",
        description = "content가 짧거나 없는 비-헤드라인 기사의 전문을 상세 API에서 가져와 업데이트합니다. 백그라운드에서 실행됩니다."
    )
    fun enrichExistingArticles(): ResponseEntity<Map<String, Any>> {
        asyncEnrichmentRunner.run()
        return ResponseEntity.accepted().body(
            mapOf("success" to true, "message" to "보강 작업이 백그라운드에서 시작되었습니다.")
        )
    }
}

@Component
class AsyncEnrichmentRunner(
    private val newsCollectionUseCase: NewsCollectionUseCase
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Async
    open fun run() {
        try {
            val count = newsCollectionUseCase.enrichExistingArticles()
            logger.info("기사 본문 보강 완료: {}건", count)
        } catch (e: Exception) {
            logger.error("기사 본문 보강 실패", e)
        }
    }
}
