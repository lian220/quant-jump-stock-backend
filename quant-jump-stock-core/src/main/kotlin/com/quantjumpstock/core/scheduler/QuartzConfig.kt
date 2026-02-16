package com.quantjumpstock.core.scheduler

import com.quantjumpstock.core.adapter.input.scheduler.*
import org.quartz.*
import org.springframework.context.annotation.ImportRuntimeHints
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import java.util.*

/**
 * Quartz 스케줄러 설정
 * 모든 Job과 Trigger를 등록합니다.
 *
 * @DependsOn - Flyway 마이그레이션이 완료된 후 Quartz가 초기화되도록 보장
 *
 * ## Job 타임라인 및 의존관계 (KST 기준)
 *
 * ```
 * 06:05  EconomicDataUpdateJob        → 미국장 마감 후 데이터 수집 (FRED + Yahoo Finance)
 * 06:30  CleanupOrdersJob             → 주문 정리
 * 07:00  PortfolioProfitReportJob     → 포트폴리오 수익 보고
 *
 * 23:00  EconomicDataUpdate2Job       → 경제 데이터 재수집
 *        ├─ CPI/NFP 발표: 21:30 KST (EDT), 22:30 KST (EST)
 *        ├─ ISM PMI 발표: 23:00 KST (EDT), 00:00 KST (EST)
 *        └─ EST/EDT 모두 커버하도록 23:00 실행
 *
 * 23:30  ParallelAnalysisJob          → 기술적 분석 (SMA/RSI/MACD) + 감정 분석
 *        └─ 의존: EconomicDataUpdate2Job 완료 후 실행
 *
 * 23:45  VertexAIPredictionJob        → AI 주가 예측 (소요 시간: ~30-35분)
 *        └─ 의존: 경제 데이터 + 기술적 분석 완료 후 실행
 *
 * 00:20  StockRecommendationJob       → 종목 추천 (Composite Score)
 *        └─ 의존: Vertex AI 예측 완료 후 실행
 *
 * 00:30  AutoBuyPlaceholderJob        → 자동 매수 (TODO: 미구현)
 *        └─ 향후 구현 예정
 *
 * 매 1분  AutoSellJob                 → 자동 매도 체크
 * ```
 *
 * ## 미국 경제지표 발표 시간 (US Eastern Time → KST)
 *
 * | 지표 | 발표 시각 (ET) | EDT (3~11월) | EST (11~3월) |
 * |------|---------------|--------------|--------------|
 * | CPI/NFP | 8:30 AM | 21:30 KST | 22:30 KST |
 * | ISM PMI | 10:00 AM | 23:00 KST | 00:00 KST |
 *
 * ## ⚠️ 알려진 제한사항: 시간 기반 의존성 체인
 *
 * 현재 파이프라인은 고정 시간 간격에 의존합니다 (완료 이벤트 기반 트리거 아님).
 * 예: 23:00 경제 데이터 → 23:30 기술적 분석 → 23:45 Vertex AI → 00:20 종목 추천
 *
 * **잠재적 문제**:
 * - 경제 데이터 수집이 23:30을 넘으면 기술적 분석이 불완전한 데이터로 실행
 * - Vertex AI 예측이 35분을 초과하면 종목 추천이 예측 완료 전에 실행
 *
 * **향후 개선**:
 * - Quartz JobListener 또는 Kafka 완료 이벤트 기반 트리거로 전환
 * - 각 Job이 이전 Job의 완료를 명시적으로 확인하도록 개선
 */
@Configuration
@DependsOn("flywayInitializer")
@ImportRuntimeHints(QuartzRuntimeHints::class)
class QuartzConfig {

    // ========================
    // 0. 경제 데이터 업데이트 (06:05) - 미국 장 마감 후 데이터 갱신
    // ========================
    @Bean
    fun economicDataUpdateJobDetail(): JobDetail {
        return JobBuilder.newJob(EconomicDataUpdateJobAdapter::class.java)
            .withIdentity("economicDataUpdateJob")
            .storeDurably()
            .build()
    }

    @Bean
    fun economicDataUpdateTrigger(): Trigger {
        return TriggerBuilder.newTrigger()
            .forJob(economicDataUpdateJobDetail())
            .withIdentity("economicDataUpdateTrigger")
            .withSchedule(
                CronScheduleBuilder.cronSchedule("0 5 6 * * ?")
                    .inTimeZone(TimeZone.getTimeZone("Asia/Seoul"))
            )
            .build()
    }

    // ========================
    // 1. 경제 데이터 재수집 (23:00)
    // ========================
    // CPI/NFP: 21:30(EDT) / 22:30(EST)
    // ISM PMI: 23:00(EDT) / 00:00(EST)
    // → 23:00 실행으로 EST/EDT 모두 커버
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
                CronScheduleBuilder.cronSchedule("0 0 23 * * ?")
                    .inTimeZone(TimeZone.getTimeZone("Asia/Seoul"))
            )
            .build()
    }

    // ========================
    // 2. 병렬 분석 (23:30) - 기술적 + 감정 분석
    // ========================
    // 경제 데이터 재수집(23:00) 완료 후 실행
    // SMA, RSI, MACD 계산 + 뉴스 감정 분석
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
                CronScheduleBuilder.cronSchedule("0 30 23 * * ?")
                    .inTimeZone(TimeZone.getTimeZone("Asia/Seoul"))
            )
            .build()
    }

    // ========================
    // 3. Vertex AI 예측 (23:45) - GCP 활성화 시에만
    // ========================
    // 경제 데이터 + 기술적 분석 완료 후 AI 예측 실행
    // 예측 소요 시간: 약 30-35분
    @Bean
    @ConditionalOnProperty(
        prefix = "gcp",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = false
    )
    fun vertexAIPredictionJobDetail(): JobDetail {
        return JobBuilder.newJob(VertexAIPredictionJobAdapter::class.java)
            .withIdentity("vertexAIPredictionJob")
            .storeDurably()
            .build()
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "gcp",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = false
    )
    fun vertexAIPredictionTrigger(): Trigger {
        return TriggerBuilder.newTrigger()
            .forJob(vertexAIPredictionJobDetail())
            .withIdentity("vertexAIPredictionTrigger")
            .withSchedule(
                CronScheduleBuilder.cronSchedule("0 45 23 * * ?")
                    .inTimeZone(TimeZone.getTimeZone("Asia/Seoul"))
            )
            .build()
    }

    // ========================
    // 4. 종목 추천 (00:20)
    // ========================
    // Vertex AI 예측 결과를 바탕으로 종목 추천
    // Composite Score: AI + Technical + Sentiment
    @Bean
    fun stockRecommendationJobDetail(): JobDetail {
        return JobBuilder.newJob(StockRecommendationJobAdapter::class.java)
            .withIdentity("stockRecommendationJob")
            .storeDurably()
            .build()
    }

    @Bean
    fun stockRecommendationTrigger(): Trigger {
        return TriggerBuilder.newTrigger()
            .forJob(stockRecommendationJobDetail())
            .withIdentity("stockRecommendationTrigger")
            .withSchedule(
                CronScheduleBuilder.cronSchedule("0 20 0 * * ?")
                    .inTimeZone(TimeZone.getTimeZone("Asia/Seoul"))
            )
            .build()
    }

    // ========================
    // 5. 자동 매수 (00:30)
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
    // 8. 뉴스 수집 (매 1분, 시간대별 스킵)
    // ========================
    // 장중(09-15시): 매분, 장전/후(06-08,16-21시): 2분, 심야(22-05시): 5분
    // Job 내부 shouldRun 로직으로 빈도 조절
    @Bean
    fun newsCollectionJobDetail(): JobDetail {
        return JobBuilder.newJob(NewsCollectionJobAdapter::class.java)
            .withIdentity("newsCollectionJob")
            .storeDurably()
            .build()
    }

    @Bean
    fun newsCollectionTrigger(): Trigger {
        return TriggerBuilder.newTrigger()
            .forJob(newsCollectionJobDetail())
            .withIdentity("newsCollectionTrigger")
            .withSchedule(
                SimpleScheduleBuilder.simpleSchedule()
                    .withIntervalInMinutes(1)
                    .repeatForever()
                    .withMisfireHandlingInstructionNextWithRemainingCount()
            )
            .build()
    }

    // ========================
    // 9. 자동 매도 체크 (매 1분)
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
