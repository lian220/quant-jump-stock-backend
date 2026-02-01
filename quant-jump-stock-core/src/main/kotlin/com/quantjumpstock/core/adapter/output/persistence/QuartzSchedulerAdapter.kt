package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.domain.model.ScheduleInfo
import com.quantjumpstock.core.domain.model.SchedulerStatus
import com.quantjumpstock.core.domain.scheduler.port.output.SchedulerRepository
import org.quartz.Scheduler
import org.quartz.TriggerKey
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Quartz Scheduler Adapter (Output Adapter)
 * SchedulerRepository 인터페이스를 구현하여 Quartz와 연동합니다.
 */
@Component
class QuartzSchedulerAdapter(
    private val scheduler: Scheduler
) : SchedulerRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun getSchedulerStatus(): SchedulerStatus {
        return try {
            val triggerKeys = scheduler.getTriggerKeys(null)
            val jobKeys = scheduler.getJobKeys(null)
            val isRunning = scheduler.isStarted && !scheduler.isInStandbyMode && !scheduler.isShutdown

            SchedulerStatus(
                isRunning = isRunning,
                scheduledJobCount = jobKeys.size,
                activeTriggerCount = triggerKeys.size
            )
        } catch (e: Exception) {
            logger.error("스케줄러 상태 조회 실패", e)
            SchedulerStatus(
                isRunning = false,
                scheduledJobCount = 0,
                activeTriggerCount = 0
            )
        }
    }

    override fun getAllSchedules(): Map<String, ScheduleInfo> {
        val schedules = mutableMapOf<String, ScheduleInfo>()

        try {
            val triggerKeys = scheduler.getTriggerKeys(null)

            for (triggerKey in triggerKeys) {
                val trigger = scheduler.getTrigger(triggerKey)
                val jobKey = trigger?.jobKey

                schedules[triggerKey.name] = ScheduleInfo(
                    triggerName = triggerKey.name,
                    jobName = jobKey?.name ?: "Unknown",
                    nextFireTime = trigger?.nextFireTime,
                    previousFireTime = trigger?.previousFireTime,
                    state = scheduler.getTriggerState(triggerKey).toString()
                )
            }
        } catch (e: Exception) {
            logger.error("스케줄 조회 중 오류 발생", e)
        }

        return schedules
    }

    override fun pauseSchedule(triggerName: String): Boolean {
        return try {
            val triggerKey = TriggerKey(triggerName)
            scheduler.pauseTrigger(triggerKey)
            logger.info("스케줄 일시 중지: $triggerName")
            true
        } catch (e: Exception) {
            logger.error("스케줄 일시 중지 실패: $triggerName", e)
            false
        }
    }

    override fun resumeSchedule(triggerName: String): Boolean {
        return try {
            val triggerKey = TriggerKey(triggerName)
            scheduler.resumeTrigger(triggerKey)
            logger.info("스케줄 재개: $triggerName")
            true
        } catch (e: Exception) {
            logger.error("스케줄 재개 실패: $triggerName", e)
            false
        }
    }

    override fun startScheduler(): Boolean {
        return try {
            if (!scheduler.isStarted) {
                scheduler.start()
                logger.info("스케줄러 시작 완료")
            } else {
                logger.info("스케줄러가 이미 실행 중입니다.")
            }
            true
        } catch (e: Exception) {
            logger.error("스케줄러 시작 실패", e)
            false
        }
    }

    override fun stopScheduler(): Boolean {
        return try {
            if (scheduler.isStarted && !scheduler.isShutdown) {
                scheduler.shutdown(false)
                logger.info("스케줄러 중지 완료")
            } else {
                logger.info("스케줄러가 이미 중지되었습니다.")
            }
            true
        } catch (e: Exception) {
            logger.error("스케줄러 중지 실패", e)
            false
        }
    }
}
