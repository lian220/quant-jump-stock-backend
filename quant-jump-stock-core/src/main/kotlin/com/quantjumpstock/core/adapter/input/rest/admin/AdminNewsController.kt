package com.quantjumpstock.core.adapter.input.rest.admin

import com.quantjumpstock.core.domain.news.port.input.NewsCollectionUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/news")
@Tag(name = "Admin News", description = "관리자용 뉴스 관리 API")
@PreAuthorize("hasRole('ADMIN')")
class AdminNewsController(
    private val newsCollectionUseCase: NewsCollectionUseCase
) {

    @PostMapping("/enrich")
    @Operation(
        summary = "기존 기사 본문 보강",
        description = "content가 짧거나 없는 비-헤드라인 기사의 전문을 상세 API에서 가져와 업데이트합니다."
    )
    fun enrichExistingArticles(): ResponseEntity<Map<String, Any>> {
        val count = newsCollectionUseCase.enrichExistingArticles()
        return ResponseEntity.ok(mapOf("success" to true, "enrichedCount" to count))
    }
}
