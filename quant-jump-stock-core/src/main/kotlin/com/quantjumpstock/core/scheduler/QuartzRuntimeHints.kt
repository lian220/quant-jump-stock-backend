package com.quantjumpstock.core.scheduler

import com.quantjumpstock.core.adapter.input.scheduler.*
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar

/**
 * Quartz Job 클래스의 GraalVM Native Image reflection 등록
 *
 * Quartz는 Job 인스턴스를 reflection으로 생성하므로,
 * GraalVM native image 빌드 시 모든 Job 클래스의 생성자를 명시적으로 등록해야 합니다.
 */
class QuartzRuntimeHints : RuntimeHintsRegistrar {

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        val jobClasses = listOf(
            AutoBuyJobAdapter::class.java,
            AutoSellJobAdapter::class.java,
            CleanupOrdersJobAdapter::class.java,
            EconomicDataUpdateJobAdapter::class.java,
            EconomicDataUpdate2JobAdapter::class.java,
            NewsCollectionJobAdapter::class.java,
            PortfolioProfitReportJobAdapter::class.java,
            StockRecommendationJobAdapter::class.java,
            VertexAIPredictionJobAdapter::class.java,
            ParallelAnalysisJob::class.java,
        )

        jobClasses.forEach { jobClass ->
            hints.reflection().registerType(
                jobClass,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
            )
        }
    }
}
