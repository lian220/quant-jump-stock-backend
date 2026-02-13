package com.quantjumpstock.core.adapter.input.rest.analysis

import com.quantjumpstock.core.domain.analysis.port.input.AnalysisUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * 분석 Controller (Input Adapter)
 * HTTP 요청을 AnalysisUseCase로 전달하는 Adapter 역할을 합니다.
 *
 * 지원하는 분석 유형:
 * - 기술적 분석 (Technical Analysis): SMA, RSI, MACD
 * - 감정 분석 (Sentiment Analysis): 뉴스 감성 점수
 * - 병렬 분석 (Parallel Analysis): 기술적 + 감정 동시 실행
 */
@Tag(name = "Analysis", description = "주식 분석 API - 기술적 분석, 감정 분석, 통합 분석")
@RestController
@RequestMapping("/api/v1/analyses")
class AnalysisController(
    private val analysisUseCase: AnalysisUseCase
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Operation(
        summary = "기술적 분석 실행",
        description = """
            기술적 지표(SMA, RSI, MACD) 기반 종목 분석을 실행합니다.

            **실행 흐름:**
            1. Kafka 이벤트 발행 (analysis.technical.request)
            2. Python Data Engine에서 분석 수행
            3. Slack 스레드로 진행상황 알림
            4. 분석 완료 후 MongoDB에 결과 저장

            **분석 지표:**
            - SMA 20: 20일 이동평균선
            - RSI: 상대강도지수 (과매수/과매도)
            - MACD: 이동평균수렴확산
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "분석 요청 성공",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(
                        value = """{
  "success": true,
  "message": "기술적 분석 요청이 Kafka에 발행되었습니다. (requestId: req-abc123)",
  "analysisType": "TECHNICAL",
  "timestamp": "2026-02-01T10:30:00Z"
}"""
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "500",
                description = "분석 요청 실패",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(
                        value = """{
  "success": false,
  "message": "기술적 분석 요청 실패: Kafka connection error",
  "timestamp": "2026-02-01T10:30:00Z"
}"""
                    )]
                )]
            )
        ]
    )
    @PostMapping("/technical")
    fun executeTechnicalAnalysis(
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?
    ): ResponseEntity<Map<String, Any>> {
        return try {
            val dateInfo = formatDateRange(startDate, endDate)
            logger.info("기술적 분석 요청: $dateInfo")

            // Kafka 이벤트 1개 발행 (날짜 범위 전달)
            analysisUseCase.triggerTechnicalAnalysis(startDate, endDate).get()

            val response = mutableMapOf<String, Any>(
                "success" to true,
                "message" to "기술적 분석 요청이 Kafka에 발행되었습니다.",
                "analysisType" to "TECHNICAL",
                "timestamp" to Instant.now().toString()
            )
            startDate?.let { response["startDate"] = it }
            endDate?.let { response["endDate"] = it }

            ResponseEntity.ok(response.toMap())
        } catch (e: Exception) {
            logger.error("기술적 분석 트리거 실패", e)
            ResponseEntity.status(500).body(
                mapOf<String, Any>(
                    "success" to false,
                    "message" to "기술적 분석 요청 실패: ${e.message}",
                    "timestamp" to Instant.now().toString()
                )
            )
        }
    }

    @Operation(
        summary = "뉴스 감정 분석 실행",
        description = """
            Alpha Vantage NEWS_SENTIMENT API를 사용하여 뉴스 감정 분석을 수행합니다.

            **분석 내용:**
            - 최근 3일간 뉴스 기사 수집
            - 종목별 감정 점수 계산 (-1 ~ +1)
            - MongoDB에 결과 저장

            **감정 점수:**
            - +0.35 ~ +1.0: 매우 긍정적 (Bullish+)
            - +0.15 ~ +0.35: 긍정적 (Bullish)
            - -0.15 ~ +0.15: 중립 (Neutral)
            - -0.35 ~ -0.15: 부정적 (Bearish)
            - -1.0 ~ -0.35: 매우 부정적 (Bearish+)
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "분석 요청 성공"),
            ApiResponse(responseCode = "500", description = "분석 요청 실패")
        ]
    )
    @PostMapping("/sentiment")
    fun executeSentimentAnalysis(
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?
    ): ResponseEntity<Map<String, Any>> {
        return try {
            val dateInfo = formatDateRange(startDate, endDate)
            logger.info("감정 분석 요청: $dateInfo")

            // Kafka 이벤트 1개 발행 (날짜 범위 전달)
            analysisUseCase.triggerSentimentAnalysis(startDate, endDate).get()

            val response = mutableMapOf<String, Any>(
                "success" to true,
                "message" to "뉴스 감정 분석 요청이 Kafka에 발행되었습니다.",
                "analysisType" to "SENTIMENT",
                "timestamp" to Instant.now().toString()
            )
            startDate?.let { response["startDate"] = it }
            endDate?.let { response["endDate"] = it }

            ResponseEntity.ok(response.toMap())
        } catch (e: Exception) {
            logger.error("감정 분석 트리거 실패", e)
            ResponseEntity.status(500).body(
                mapOf<String, Any>(
                    "success" to false,
                    "message" to "감정 분석 요청 실패: ${e.message}",
                    "timestamp" to Instant.now().toString()
                )
            )
        }
    }

    @Operation(
        summary = "병렬 분석 실행 (기술적 + 감정 동시)",
        description = """
            기술적 분석과 감정 분석을 동시에 실행합니다.

            **장점:**
            - 실행 시간 단축 (약 50% 절감)
            - 각 분석 독립적으로 완료
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "병렬 분석 요청 성공"),
            ApiResponse(responseCode = "500", description = "병렬 분석 요청 실패")
        ]
    )
    @PostMapping("/parallel")
    fun executeParallelAnalysis(): ResponseEntity<Map<String, Any>> {
        return try {
            logger.info("병렬 분석 수동 트리거 요청 받음")

            // 기술적 분석과 감정 분석을 동시에 트리거
            val technicalFuture = analysisUseCase.triggerTechnicalAnalysis()
            val sentimentFuture = analysisUseCase.triggerSentimentAnalysis()

            // 두 분석 모두 완료될 때까지 대기
            java.util.concurrent.CompletableFuture.allOf(technicalFuture, sentimentFuture)
                .thenApply {
                    ResponseEntity.ok(
                        mapOf<String, Any>(
                            "success" to true,
                            "message" to "병렬 분석 요청이 Kafka에 발행되었습니다.",
                            "analysisType" to "PARALLEL",
                            "details" to mapOf(
                                "technical" to technicalFuture.get(),
                                "sentiment" to sentimentFuture.get()
                            ),
                            "timestamp" to Instant.now().toString()
                        )
                    )
                }
                .get()
        } catch (e: Exception) {
            logger.error("병렬 분석 트리거 실패", e)
            ResponseEntity.status(500).body(
                mapOf<String, Any>(
                    "success" to false,
                    "message" to "병렬 분석 요청 실패: ${e.message}",
                    "timestamp" to Instant.now().toString()
                )
            )
        }
    }

    @Operation(
        summary = "분석 서비스 상태 조회",
        description = """
            분석 서비스의 현재 상태와 스케줄 정보를 조회합니다.

            **포함 정보:**
            - 서비스 상태 (running/stopped)
            - 등록된 스케줄 목록
            - 사용 가능한 API 엔드포인트
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "상태 조회 성공"),
            ApiResponse(responseCode = "500", description = "상태 조회 실패")
        ]
    )
    @GetMapping("/status")
    fun getStatus(): ResponseEntity<Map<String, Any>> {
        return try {
            ResponseEntity.ok(
                mapOf(
                    "status" to "running",
                    "service" to "analysis-scheduler",
                    "timestamp" to Instant.now().toString(),
                    "schedules" to listOf(
                        mapOf(
                            "name" to "parallelAnalysis",
                            "time" to "23:05 (KST)",
                            "description" to "병렬 분석 (기술적 + 감정 동시 실행)"
                        )
                    ),
                    "availableEndpoints" to listOf(
                        "POST /api/v1/analyses/technical - 기술적 분석만 실행",
                        "POST /api/v1/analyses/sentiment - 감정 분석만 실행",
                        "POST /api/v1/analyses/parallel - 병렬 분석 실행 (동시)",
                        "GET  /api/v1/analyses/status - 상태 조회"
                    )
                )
            )
        } catch (e: Exception) {
            logger.error("상태 조회 중 오류", e)
            ResponseEntity.status(500).body(
                mapOf<String, Any>(
                    "status" to "error",
                    "message" to (e.message ?: "Unknown error")
                )
            )
        }
    }

    private fun formatDateRange(startDate: String?, endDate: String?): String {
        return when {
            startDate != null && endDate != null -> "$startDate ~ $endDate"
            startDate != null -> "$startDate ~ 오늘"
            else -> "자동 (마지막 수집일+1 ~ 오늘)"
        }
    }
}
