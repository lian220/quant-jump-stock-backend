package com.quantjumpstock.core.adapter.input.scheduler

import com.quantjumpstock.core.domain.news.port.input.NewsCollectionUseCase
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class NewsCollectionJobAdapter(
    private val newsCollectionUseCase: NewsCollectionUseCase
) : Job {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun execute(context: JobExecutionContext?) {
        try {
            newsCollectionUseCase.collectAll()
        } catch (e: Exception) {
            logger.error("뉴스 수집 Job 실행 중 오류", e)
            throw JobExecutionException(e)
        }
    }
}
