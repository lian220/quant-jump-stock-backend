package com.quantjumpstock.core.adapter.input.rest.admin

import com.quantjumpstock.core.domain.news.port.input.NewsCollectionUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
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
    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostMapping("/collect")
    @Operation(
        summary = "뉴스 수집 요청 (Pub/Sub)",
        description = "Data Engine에 뉴스 수집 요청을 발행합니다. 수집 시 상세 본문도 함께 가져옵니다."
    )
    fun requestCollection(): ResponseEntity<Map<String, Any>> {
        newsCollectionUseCase.requestCollection()
        return ResponseEntity.accepted().body(
            mapOf("success" to true, "message" to "뉴스 수집 요청이 발행되었습니다.")
        )
    }
}
