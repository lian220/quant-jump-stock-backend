package com.quantjumpstock.core.adapter.input.rest.marketplace

import com.quantjumpstock.core.application.marketplace.MarketplaceService
import com.quantjumpstock.core.application.marketplace.StrategyListRequest
import com.quantjumpstock.core.application.marketplace.StrategyListResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

/**
 * Marketplace Controller
 * 공개 전략 목록 및 마켓플레이스 관련 API
 */
@RestController
@RequestMapping("/api/v1/marketplace")
@Tag(name = "Marketplace", description = "마켓플레이스 API - 공개 전략 조회")
@CrossOrigin(origins = ["http://localhost:3000", "http://localhost:4000"], allowCredentials = "true")
class MarketplaceController(
    private val marketplaceService: MarketplaceService
) {

    /**
     * 공개 전략 목록 조회
     * GET /api/v1/marketplace/strategies
     */
    @GetMapping("/strategies")
    @Operation(
        summary = "공개 전략 목록 조회",
        description = """
            공개된 활성 전략 목록을 조회합니다.

            필터링 옵션:
            - category: 전략 카테고리 (VALUE, MOMENTUM, ASSET_ALLOCATION, QUANT_COMPOSITE, SEASONAL, ML_PREDICTION)
            - minCagr: 최소 CAGR (%) - 예: 10.5
            - maxMdd: 최대 MDD (%) - 예: -20.0

            정렬 옵션:
            - subscribers: 구독자 수 (기본값)
            - cagr: CAGR (높은 순)
            - sharpe: 샤프 비율 (높은 순)
            - recent: 최신순

            페이징:
            - page: 페이지 번호 (0부터 시작, 기본값: 0)
            - size: 페이지 크기 (기본값: 20)
        """
    )
    fun getPublicStrategies(
        @Parameter(description = "카테고리 코드 필터 (VALUE, MOMENTUM, ASSET_ALLOCATION, QUANT_COMPOSITE, SEASONAL, CUSTOM, ML_PREDICTION)")
        @RequestParam(required = false) categoryCode: String?,

        @Parameter(description = "최소 CAGR (%)", example = "10.0")
        @RequestParam(required = false) minCagr: BigDecimal?,

        @Parameter(description = "최대 MDD (%)", example = "-20.0")
        @RequestParam(required = false) maxMdd: BigDecimal?,

        @Parameter(description = "정렬 기준 (subscribers, cagr, sharpe, recent)", example = "subscribers")
        @RequestParam(required = false, defaultValue = "subscribers") sortBy: String?,

        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
        @RequestParam(required = false, defaultValue = "0") page: Int,

        @Parameter(description = "페이지 크기", example = "20")
        @RequestParam(required = false, defaultValue = "20") size: Int
    ): ResponseEntity<StrategyListResponse> {
        val request = StrategyListRequest(
            categoryCode = categoryCode,
            minCagr = minCagr,
            maxMdd = maxMdd,
            sortBy = sortBy,
            page = page,
            size = size
        )

        val response = marketplaceService.getPublicStrategies(request)
        return ResponseEntity.ok(response)
    }
}
