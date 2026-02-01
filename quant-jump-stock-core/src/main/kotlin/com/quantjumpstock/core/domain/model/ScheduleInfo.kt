package com.quantjumpstock.core.domain.model

import java.util.*

/**
 * 스케줄 정보 도메인 모델
 */
data class ScheduleInfo(
    val triggerName: String,
    val jobName: String,
    val nextFireTime: Date?,
    val previousFireTime: Date?,
    val state: String
)
