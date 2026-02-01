package com.quantjumpstock.core.adapter.input.rest.economic

import com.quantjumpstock.core.domain.economic.port.input.EconomicDataUseCase
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
import java.time.LocalDate
import java.util.concurrent.CompletableFuture

/**
 * 경제 데이터 수집 Controller (Input Adapter)
 * HTTP 요청을 UseCase로 전달하는 Adapter 역할을 합니다.
 */
@Tag(name = "Economic Data", description = "경제 데이터 수집 API - FRED, Yahoo Finance")
@RestController
@RequestMapping("/api/v1/economic-data")
class EconomicDataController(
    private val economicDataUseCase: EconomicDataUseCase
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Operation(
        summary = "경제 데이터 수집 실행",
        description = """
            FRED API와 Yahoo Finance를 통해 경제 지표 데이터를 수집합니다.

            **수집 데이터:**
            - FRED: GDP, 실업률, 인플레이션, 금리 등
            - Yahoo Finance: S&P500, NASDAQ, 달러인덱스 등

            **날짜 범위 지정:**
            - startDate, endDate 모두 없음: 당일 데이터 수집
            - startDate만 지정: 해당 날짜만 수집
            - startDate, endDate 모두 지정: 범위 내 모든 날짜 수집

            **실행 흐름:**
            1. 각 날짜마다 별도 Kafka 이벤트 발행
            2. Python Data Engine에서 날짜별 데이터 수집
            3. Slack 스레드로 진행상황 알림
            4. MongoDB에 저장

            **자동 스케줄:**
            - 매일 06:05 (KST): 1차 수집
            - 매일 23:00 (KST): 2차 재수집
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "데이터 수집 요청 성공",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(
                        value = """{
  "success": true,
  "message": "경제 데이터 수집 요청 완료",
  "dates": ["2026-01-01", "2026-01-02"],
  "count": 2,
  "timestamp": "2026-02-01T06:05:00Z"
}"""
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "500",
                description = "데이터 수집 요청 실패"
            )
        ]
    )
    @PostMapping("/collections")
    fun collectEconomicData(
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?
    ): ResponseEntity<Map<String, Any>> {
        return try {
            // 날짜 범위 계산
            val dates = when {
                startDate != null && endDate != null -> {
                    val start = LocalDate.parse(startDate)
                    val end = LocalDate.parse(endDate)
                    generateDateRange(start, end)
                }
                startDate != null -> {
                    listOf(LocalDate.parse(startDate))
                }
                else -> {
                    listOf(LocalDate.now())
                }
            }

            logger.info("경제 데이터 수집 요청: ${dates.size}개 날짜 (${dates.first()} ~ ${dates.last()})")

            // 각 날짜마다 별도 이벤트 발행
            val futures = dates.map { date ->
                economicDataUseCase.triggerEconomicDataUpdate(date.toString())
            }

            // 모든 요청 완료 대기
            CompletableFuture.allOf(*futures.toTypedArray()).get()

            ResponseEntity.ok(
                mapOf<String, Any>(
                    "success" to true,
                    "message" to "경제 데이터 수집 요청 완료",
                    "dates" to dates.map { it.toString() },
                    "count" to dates.size,
                    "timestamp" to Instant.now().toString()
                )
            )
        } catch (e: Exception) {
            logger.error("경제 데이터 수집 요청 실패", e)
            ResponseEntity.status(500).body(
                mapOf<String, Any>(
                    "success" to false,
                    "message" to "경제 데이터 수집 요청 실패: ${e.message}",
                    "timestamp" to Instant.now().toString()
                )
            )
        }
    }

    /**
     * 날짜 범위 생성
     */
    private fun generateDateRange(start: LocalDate, end: LocalDate): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var current = start
        while (!current.isAfter(end)) {
            dates.add(current)
            current = current.plusDays(1)
        }
        return dates
    }

    @Operation(
        summary = "경제 데이터 서비스 상태 조회",
        description = "경제 데이터 수집 서비스의 현재 상태와 스케줄 정보를 조회합니다."
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
                    "service" to "economic-data-scheduler",
                    "timestamp" to Instant.now().toString(),
                    "schedules" to listOf(
                        mapOf(
                            "name" to "economicDataUpdate1",
                            "time" to "06:05 (KST)",
                            "description" to "경제 데이터 업데이트"
                        ),
                        mapOf(
                            "name" to "economicDataUpdate2",
                            "time" to "23:00 (KST)",
                            "description" to "경제 데이터 재수집 및 Vertex AI 예측 병렬 실행"
                        )
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
}
