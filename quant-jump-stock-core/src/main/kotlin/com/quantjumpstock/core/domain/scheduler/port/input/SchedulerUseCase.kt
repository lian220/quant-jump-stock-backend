package com.quantjumpstock.core.domain.scheduler.port.input

import com.quantjumpstock.core.domain.model.ScheduleInfo
import com.quantjumpstock.core.domain.model.SchedulerStatus

/**
 * 스케줄러 관리 Use Case (Input Port)
 * 비즈니스 로직의 인터페이스를 정의합니다.
 */
interface SchedulerUseCase {
    /**
     * 스케줄러 상태 조회
     */
    fun getSchedulerStatus(): SchedulerStatus

    /**
     * 모든 스케줄 조회
     */
    fun getAllSchedules(): Map<String, ScheduleInfo>

    /**
     * 특정 스케줄 일시 중지
     */
    fun pauseSchedule(triggerName: String): Boolean

    /**
     * 특정 스케줄 재개
     */
    fun resumeSchedule(triggerName: String): Boolean

    /**
     * 스케줄러 시작
     */
    fun startScheduler(): Boolean

    /**
     * 스케줄러 중지
     */
    fun stopScheduler(): Boolean
}
