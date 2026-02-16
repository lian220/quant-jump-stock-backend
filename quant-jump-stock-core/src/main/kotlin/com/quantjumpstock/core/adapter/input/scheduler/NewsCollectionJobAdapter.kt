package com.quantjumpstock.core.adapter.input.scheduler

import com.quantjumpstock.core.domain.news.port.input.NewsCollectionUseCase
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalTime
import java.time.ZoneId

@Component
class NewsCollectionJobAdapter(
    private val newsCollectionUseCase: NewsCollectionUseCase
) : Job {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val kst = ZoneId.of("Asia/Seoul")

    override fun execute(context: JobExecutionContext?) {
        val now = LocalTime.now(kst)
        val hour = now.hour
        val minute = now.minute

        // 시간대별 스킵 로직
        val shouldRun = when {
            hour in 9..15 -> true                     // 장중: 매분 실행
            hour in 6..8 || hour in 16..21 -> minute % 2 == 0  // 장전/후: 2분 간격
            else -> minute % 5 == 0                   // 심야(22~05): 5분 간격
        }

        if (!shouldRun) {
            logger.debug("뉴스 수집 스킵 (KST {}:{}, 시간대별 빈도 조절)", hour, minute)
            return
        }

        try {
            newsCollectionUseCase.requestCollection()
        } catch (e: Exception) {
            logger.error("뉴스 수집 요청 Pub/Sub 발행 중 오류", e)
            throw JobExecutionException(e)
        }
    }
}
