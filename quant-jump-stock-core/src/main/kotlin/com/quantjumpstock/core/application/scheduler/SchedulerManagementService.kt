package com.quantjumpstock.core.application.scheduler

import com.quantjumpstock.core.domain.model.ScheduleInfo
import com.quantjumpstock.core.domain.model.SchedulerStatus
import com.quantjumpstock.core.domain.scheduler.port.input.SchedulerUseCase
import com.quantjumpstock.core.domain.scheduler.port.output.SchedulerRepository
import org.springframework.stereotype.Service

/**
 * 스케줄러 관리 Use Case 구현체 (Application Layer)
 * 비즈니스 로직을 구현하고 Output Port를 통해 외부 시스템과 상호작용합니다.
 */
@Service
class SchedulerManagementService(
    private val schedulerRepository: SchedulerRepository
) : SchedulerUseCase {

    override fun getSchedulerStatus(): SchedulerStatus {
        return schedulerRepository.getSchedulerStatus()
    }

    override fun getAllSchedules(): Map<String, ScheduleInfo> {
        return schedulerRepository.getAllSchedules()
    }

    override fun pauseSchedule(triggerName: String): Boolean {
        return schedulerRepository.pauseSchedule(triggerName)
    }

    override fun resumeSchedule(triggerName: String): Boolean {
        return schedulerRepository.resumeSchedule(triggerName)
    }

    override fun startScheduler(): Boolean {
        return schedulerRepository.startScheduler()
    }

    override fun stopScheduler(): Boolean {
        return schedulerRepository.stopScheduler()
    }
}
