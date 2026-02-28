package com.quantjumpstock.core.scheduler

import com.quantjumpstock.core.adapter.input.scheduler.*
import org.quartz.CronScheduleBuilder
import org.quartz.JobBuilder
import org.quartz.JobDetail
import org.quartz.SimpleScheduleBuilder
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import org.springframework.context.annotation.ImportRuntimeHints
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import java.util.*

/**
 * Quartz 스케줄러 설정
 * 트레이딩 관련 Job과 Trigger를 등록합니다.
 *
 * @DependsOn - Flyway 마이그레이션이 완료된 후 Quartz가 초기화되도록 보장
 *
 * ## Job 타임라인 (KST 기준)
 *
 * ```
 * ── Cloud Scheduler (데이터 파이프라인, Pub/Sub → Data Engine) ──
 * 06:05  morning-economic-data        → 경제데이터 수집 (단독, source=standalone)
 * 23:40  evening-pipeline             → 경제데이터 → 분석 → Vertex AI (체이닝, source=pipeline)
 * 00:20  stock-recommendation         → 종목 추천 (단독, source=standalone)
 *
 * ── Core Quartz (트레이딩) ──
 * 06:30  CleanupOrdersJob             → 주문 정리
 * 07:00  PortfolioProfitReportJob     → 포트폴리오 수익 보고
 * 00:30  AutoBuyPlaceholderJob        → 자동 매수
 * 매 1분  AutoSellJob                 → 자동 매도 체크
 *
 * ── Core Quartz (백테스트) ──
 * 일 02:00  CanonicalBacktestJob      → PUBLISHED 전략 대표 백테스트
 * 매일 03:00  BacktestCleanupJob      → RUNNING 타임아웃, 초과 아카이브 정리
 * ```
 *
 * ## source 구분 (파이프라인 체이닝 제어)
 *
 * | source       | 트리거                          | cascade 동작                      |
 * |--------------|--------------------------------|----------------------------------|
 * | "pipeline"   | Cloud Scheduler evening-pipeline | 완료 시 다음 단계 자동 트리거 |
 * | "standalone" | 그 외 모든 트리거 (Admin API, Cloud Scheduler 단독 등) | 단독 실행, cascade 없음 |
 */
@Configuration
@DependsOn("flywayInitializer")
@ImportRuntimeHints(QuartzRuntimeHints::class)
class QuartzConfig {

    // ========================
    // 1. 자동 매수 (00:30)
    // ========================
    // 미국 정규장 개장 1시간 후 자동 매수
    // 초기 변동성 진정, 트렌드 확정 후 안전한 진입
    // 참고: AutoBuyJobAdapter는 실제 트레이딩 로직 포함
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
    // 2. 주문 정리 (06:30)
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
    // 3. 포트폴리오 수익 보고 (07:00)
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
    // 4. 자동 매도 체크 (매 1분)
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

    // ========================
    // 5. Canonical 백테스트 갱신 (매주 일요일 02:00)
    // ========================
    // PUBLISHED 전략 대상 대표 백테스트 자동 실행
    @Bean
    fun canonicalBacktestJobDetail(): JobDetail {
        return JobBuilder.newJob(CanonicalBacktestJobAdapter::class.java)
            .withIdentity("canonicalBacktestJob")
            .storeDurably()
            .build()
    }

    @Bean
    fun canonicalBacktestTrigger(): Trigger {
        return TriggerBuilder.newTrigger()
            .forJob(canonicalBacktestJobDetail())
            .withIdentity("canonicalBacktestTrigger")
            .withSchedule(
                CronScheduleBuilder.cronSchedule("0 0 2 ? * SUN")
                    .inTimeZone(TimeZone.getTimeZone("Asia/Seoul"))
            )
            .build()
    }

    // ========================
    // 6. 백테스트 데이터 정리 (매일 03:00)
    // ========================
    // RUNNING 타임아웃, 초과 USER_CUSTOM 아카이브, 오래된 CANONICAL 정리
    @Bean
    fun backtestCleanupJobDetail(): JobDetail {
        return JobBuilder.newJob(BacktestCleanupJobAdapter::class.java)
            .withIdentity("backtestCleanupJob")
            .storeDurably()
            .build()
    }

    @Bean
    fun backtestCleanupTrigger(): Trigger {
        return TriggerBuilder.newTrigger()
            .forJob(backtestCleanupJobDetail())
            .withIdentity("backtestCleanupTrigger")
            .withSchedule(
                CronScheduleBuilder.cronSchedule("0 0 3 * * ?")
                    .inTimeZone(TimeZone.getTimeZone("Asia/Seoul"))
            )
            .build()
    }

}
