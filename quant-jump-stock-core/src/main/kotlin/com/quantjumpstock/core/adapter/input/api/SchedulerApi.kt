package com.quantjumpstock.core.adapter.input.api

import com.quantjumpstock.core.domain.model.ScheduleInfo
import com.quantjumpstock.core.domain.model.SchedulerStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping

/**
 * 스케줄러 관리 API 스펙
 */
@Tag(name = "Scheduler", description = "스케줄러 관리 API - Quartz Scheduler 제어 및 모니터링")
interface SchedulerApi {

    @GetMapping("/status")
    @Operation(
        summary = "스케줄러 상태 조회",
        description = """
            Quartz Scheduler의 전체 상태를 조회합니다.

            **조회 정보:**
            - 스케줄러 실행 상태 (running/stopped/paused)
            - 전체 Job 개수
            - 실행 중인 Job 개수
            - 스케줄러 인스턴스 이름
            - 시작 시간 및 실행 시간
        """
    )
    @SchedulerStatusResponses
    fun getSchedulerStatus(): ResponseEntity<SchedulerStatus>

    @GetMapping("/schedules")
    @Operation(
        summary = "전체 스케줄 목록 조회",
        description = """
            등록된 모든 스케줄의 상태와 설정 정보를 조회합니다.

            **등록된 스케줄:**
            1. economicDataUpdate2Trigger (22:00 KST): 경제 데이터 재수집 + Vertex AI 예측
            2. parallelAnalysisTrigger (23:05 KST): 병렬 분석 (기술적 + 감정)
            3. autoBuyTrigger (00:30 KST): 자동 매수
            4. cleanupOrdersTrigger (06:30 KST): 주문 정리
            5. portfolioProfitReportTrigger (07:00 KST): 포트폴리오 수익 보고
            6. autoSellTrigger (매 1분): 자동 매도 체크
        """
    )
    @StandardApiResponses
    fun getAllSchedules(): ResponseEntity<Map<String, ScheduleInfo>>

    @PostMapping("/schedules/{triggerName}/pause")
    @Operation(
        summary = "특정 스케줄 일시 중지",
        description = "지정된 스케줄의 실행을 일시 중지합니다."
    )
    @SchedulerSuccessResponse
    fun pauseSchedule(
        @PathVariable @Parameter(description = "일시 중지할 Trigger 이름", example = "economicDataUpdateTrigger")
        triggerName: String
    ): ResponseEntity<Map<String, Any>>

    @PostMapping("/schedules/{triggerName}/resume")
    @Operation(
        summary = "일시 중지된 스케줄 재개",
        description = "일시 중지된 스케줄을 다시 활성화합니다."
    )
    @SchedulerSuccessResponse
    fun resumeSchedule(
        @PathVariable @Parameter(description = "재개할 Trigger 이름", example = "economicDataUpdateTrigger")
        triggerName: String
    ): ResponseEntity<Map<String, Any>>

    @PostMapping("/start")
    @Operation(
        summary = "스케줄러 시작",
        description = """
            중지된 스케줄러를 시작합니다.

            **효과:**
            - 모든 등록된 스케줄이 활성화됩니다.
            - 각 스케줄은 설정된 시간에 자동 실행됩니다.
        """
    )
    @SchedulerSuccessResponse
    fun startScheduler(): ResponseEntity<Map<String, Any>>

    @PostMapping("/stop")
    @Operation(
        summary = "스케줄러 중지",
        description = """
            실행 중인 스케줄러를 중지합니다.

            **주의:**
            - 모든 예약된 작업이 실행되지 않습니다.
            - 실행 중인 Job은 완료될 때까지 대기합니다.
        """
    )
    @SchedulerSuccessResponse
    fun stopScheduler(): ResponseEntity<Map<String, Any>>
}
