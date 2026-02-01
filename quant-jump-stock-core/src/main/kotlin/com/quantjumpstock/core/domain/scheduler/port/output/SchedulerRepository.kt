package com.quantjumpstock.core.domain.scheduler.port.output

import com.quantjumpstock.core.domain.model.ScheduleInfo
import com.quantjumpstock.core.domain.model.SchedulerStatus

/**
 * 스케줄러 저장소 인터페이스 (Output Port)
 * Quartz와 같은 스케줄러 엔진에 접근하는 인터페이스입니다.
 */
interface SchedulerRepository {
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
