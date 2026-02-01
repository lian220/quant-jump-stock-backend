package com.quantjumpstock.core.adapter.input.rest.scheduler

import com.quantjumpstock.core.adapter.input.api.SchedulerApi
import com.quantjumpstock.core.domain.model.ScheduleInfo
import com.quantjumpstock.core.domain.model.SchedulerStatus
import com.quantjumpstock.core.domain.scheduler.port.input.SchedulerUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 스케줄러 관리 Controller 구현체
 * HTTP 요청을 UseCase로 전달하는 Adapter 역할을 합니다.
 */
@RestController
@RequestMapping("/api/v1/scheduler")
class SchedulerController(
    private val schedulerUseCase: SchedulerUseCase
) : SchedulerApi {

    override fun getSchedulerStatus(): ResponseEntity<SchedulerStatus> {
        return ResponseEntity.ok(schedulerUseCase.getSchedulerStatus())
    }

    override fun getAllSchedules(): ResponseEntity<Map<String, ScheduleInfo>> {
        return ResponseEntity.ok(schedulerUseCase.getAllSchedules())
    }

    override fun pauseSchedule(triggerName: String): ResponseEntity<Map<String, Any>> {
        val success = schedulerUseCase.pauseSchedule(triggerName)
        return ResponseEntity.ok(
            mapOf(
                "success" to success,
                "message" to if (success) "스케줄이 일시 중지되었습니다." else "스케줄 일시 중지 실패"
            )
        )
    }

    override fun resumeSchedule(triggerName: String): ResponseEntity<Map<String, Any>> {
        val success = schedulerUseCase.resumeSchedule(triggerName)
        return ResponseEntity.ok(
            mapOf(
                "success" to success,
                "message" to if (success) "스케줄이 재개되었습니다." else "스케줄 재개 실패"
            )
        )
    }

    override fun startScheduler(): ResponseEntity<Map<String, Any>> {
        val success = schedulerUseCase.startScheduler()
        return ResponseEntity.ok(
            mapOf(
                "success" to success,
                "message" to if (success) "스케줄러가 시작되었습니다." else "스케줄러 시작 실패"
            )
        )
    }

    override fun stopScheduler(): ResponseEntity<Map<String, Any>> {
        val success = schedulerUseCase.stopScheduler()
        return ResponseEntity.ok(
            mapOf(
                "success" to success,
                "message" to if (success) "스케줄러가 중지되었습니다." else "스케줄러 중지 실패"
            )
        )
    }
}
