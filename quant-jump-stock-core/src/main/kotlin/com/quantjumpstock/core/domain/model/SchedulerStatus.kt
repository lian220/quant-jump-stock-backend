package com.quantjumpstock.core.domain.model

/**
 * 스케줄러 상태 도메인 모델
 */
data class SchedulerStatus(
    val isRunning: Boolean,
    val scheduledJobCount: Int,
    val activeTriggerCount: Int
)
