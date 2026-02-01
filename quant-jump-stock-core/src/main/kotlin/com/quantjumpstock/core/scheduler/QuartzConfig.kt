package com.quantjumpstock.core.scheduler

import com.quantjumpstock.core.adapter.input.scheduler.*
import org.quartz.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

/**
 * Quartz 스케줄러 설정
 * 모든 Job과 Trigger를 등록합니다.
 */
@Configuration
class QuartzConfig {

    // ========================
    // 1. 경제 데이터 수집 + Vertex AI 예측 (22:00)
    // ========================
    // CPI/NFP 발표(21:30) 직후, ISM PMI(23:00) 직전
    // Vertex AI 예측 실행 (Fine-tuning 3-5분)
    @Bean
    fun economicDataUpdate2JobDetail(): JobDetail {
        return JobBuilder.newJob(EconomicDataUpdate2JobAdapter::class.java)
            .withIdentity("economicDataUpdate2Job")
            .storeDurably()
            .build()
    }

    @Bean
    fun economicDataUpdate2Trigger(): Trigger {
        return TriggerBuilder.newTrigger()
            .forJob(economicDataUpdate2JobDetail())
            .withIdentity("economicDataUpdate2Trigger")
            .withSchedule(
                CronScheduleBuilder.cronSchedule("0 0 22 * * ?")
                    .inTimeZone(TimeZone.getTimeZone("Asia/Seoul"))
            )
            .build()
    }

    // ========================
    // 2. 병렬 분석 (23:05) - 검증용
    // ========================
    @Bean
    fun parallelAnalysisJobDetail(): JobDetail {
        return JobBuilder.newJob(ParallelAnalysisJob::class.java)
            .withIdentity("parallelAnalysisJob")
            .storeDurably()
            .build()
    }

    @Bean
    fun parallelAnalysisTrigger(): Trigger {
        return TriggerBuilder.newTrigger()
            .forJob(parallelAnalysisJobDetail())
            .withIdentity("parallelAnalysisTrigger")
            .withSchedule(
                CronScheduleBuilder.cronSchedule("0 5 23 * * ?")
                    .inTimeZone(TimeZone.getTimeZone("Asia/Seoul"))
            )
            .build()
    }

    // ========================
    // 3. 통합 분석 (01:30) - 검증용
    // ========================
    // 매수 후 기술적 분석 결과 검증
    @Bean
    fun combinedAnalysisJobDetail(): JobDetail {
        return JobBuilder.newJob(CombinedAnalysisJobAdapter::class.java)
            .withIdentity("combinedAnalysisJob")
            .storeDurably()
            .build()
    }

    @Bean
    fun combinedAnalysisTrigger(): Trigger {
        return TriggerBuilder.newTrigger()
            .forJob(combinedAnalysisJobDetail())
            .withIdentity("combinedAnalysisTrigger")
            .withSchedule(
                CronScheduleBuilder.cronSchedule("0 30 1 * * ?")
                    .inTimeZone(TimeZone.getTimeZone("Asia/Seoul"))
            )
            .build()
    }

    // ========================
    // 4. 자동 매수 (00:30) - 미국 정규장 개장 1시간 후
    // ========================
    // 초기 변동성 진정, 트렌드 확정 후 안전한 진입
    @Bean
    fun autoBuyJobDetail(): JobDetail {
        return JobBuilder.newJob(AutoBuyJobAdapter::class.java)
            .withIdentity("autoBuyJob")
            .storeDurably()
            .build()
    }

    @Bean
    fun autoBuyTrigger(): Trigger {
        return TriggerBuilder.newTrigger()
            .forJob(autoBuyJobDetail())
            .withIdentity("autoBuyTrigger")
            .withSchedule(
                CronScheduleBuilder.cronSchedule("0 30 0 * * ?")
                    .inTimeZone(TimeZone.getTimeZone("Asia/Seoul"))
            )
            .build()
    }

    // ========================
    // 5. 감정 분석 (02:30) - 검증용
    // ========================
    // 뉴스 감정 분석 결과로 매수 결정 검증
    @Bean
    fun sentimentAnalysisJobDetail(): JobDetail {
        return JobBuilder.newJob(ParallelAnalysisJob::class.java)
            .withIdentity("sentimentAnalysisJob")
            .storeDurably()
            .build()
    }

    @Bean
    fun sentimentAnalysisTrigger(): Trigger {
        return TriggerBuilder.newTrigger()
            .forJob(sentimentAnalysisJobDetail())
            .withIdentity("sentimentAnalysisTrigger")
            .withSchedule(
                CronScheduleBuilder.cronSchedule("0 30 2 * * ?")
                    .inTimeZone(TimeZone.getTimeZone("Asia/Seoul"))
            )
            .build()
    }

    // ========================
    // 6. 주문 정리 (06:30)
    // ========================
    @Bean
    fun cleanupOrdersJobDetail(): JobDetail {
        return JobBuilder.newJob(CleanupOrdersJobAdapter::class.java)
            .withIdentity("cleanupOrdersJob")
            .storeDurably()
            .build()
    }

    @Bean
    fun cleanupOrdersTrigger(): Trigger {
        return TriggerBuilder.newTrigger()
            .forJob(cleanupOrdersJobDetail())
            .withIdentity("cleanupOrdersTrigger")
            .withSchedule(
                CronScheduleBuilder.cronSchedule("0 30 6 * * ?")
                    .inTimeZone(TimeZone.getTimeZone("Asia/Seoul"))
            )
            .build()
    }

    // ========================
    // 7. 포트폴리오 수익 보고 (07:00)
    // ========================
    @Bean
    fun portfolioProfitReportJobDetail(): JobDetail {
        return JobBuilder.newJob(PortfolioProfitReportJobAdapter::class.java)
            .withIdentity("portfolioProfitReportJob")
            .storeDurably()
            .build()
    }

    @Bean
    fun portfolioProfitReportTrigger(): Trigger {
        return TriggerBuilder.newTrigger()
            .forJob(portfolioProfitReportJobDetail())
            .withIdentity("portfolioProfitReportTrigger")
            .withSchedule(
                CronScheduleBuilder.cronSchedule("0 0 7 * * ?")
                    .inTimeZone(TimeZone.getTimeZone("Asia/Seoul"))
            )
            .build()
    }

    // ========================
    // 8. 자동 매도 체크 (매 1분)
    // ========================
    @Bean
    fun autoSellJobDetail(): JobDetail {
        return JobBuilder.newJob(AutoSellJobAdapter::class.java)
            .withIdentity("autoSellJob")
            .storeDurably()
            .build()
    }

    @Bean
    fun autoSellTrigger(): Trigger {
        return TriggerBuilder.newTrigger()
            .forJob(autoSellJobDetail())
            .withIdentity("autoSellTrigger")
            .withSchedule(
                SimpleScheduleBuilder.simpleSchedule()
                    .withIntervalInMinutes(1)
                    .repeatForever()
            )
            .build()
    }

}
