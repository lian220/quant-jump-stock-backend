package com.quantjumpstock.core.adapter.input.rest.marketplace

import com.quantjumpstock.core.application.marketplace.MarketplaceService
import com.quantjumpstock.core.application.marketplace.StrategyDetailResponse
import com.quantjumpstock.core.application.marketplace.StrategyListRequest
import com.quantjumpstock.core.application.marketplace.StrategyListResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
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

    /**
     * 전략 상세 조회
     * GET /api/v1/marketplace/strategies/{id}
     */
    @GetMapping("/strategies/{id}")
    @Operation(
        summary = "전략 상세 조회",
        description = """
            공개된 전략의 상세 정보를 조회합니다.

            응답에 포함되는 정보:
            - 기본 정보: 전략명, 설명, 카테고리, 리밸런싱 주기
            - 성과 지표: CAGR, MDD, 샤프 비율, 소르티노 비율, 승률 등
            - 수익 곡선: 백테스트 기간 동안의 자산 가치 변화
            - 현재 보유 종목: 최근 30일 내 매수/리밸런싱 신호 기반
        """
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(schema = Schema(implementation = StrategyDetailResponse::class))]
        ),
        ApiResponse(
            responseCode = "404",
            description = "전략을 찾을 수 없음"
        )
    )
    fun getStrategyDetail(
        @Parameter(description = "전략 ID", example = "1")
        @PathVariable id: Long
    ): ResponseEntity<StrategyDetailResponse> {
        val response = marketplaceService.getStrategyDetail(id)
        return ResponseEntity.ok(response)
    }
}
