package com.quantjumpstock.core.application.backtest

import com.quantjumpstock.core.domain.model.backtest.GradedMetric
import com.quantjumpstock.core.domain.model.backtest.MetricGrade
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 초보자용 백테스트 Enhanced 응답
 * GET /api/v1/backtest/{id}/enhanced
 *
 * SCRUM-245: 백테스트 결과 UX 강화
 * - 신호등 시스템 (good/warning/danger)
 * - 평문 설명 ("1000만원 → 1152만원")
 * - 용어 설명 (15개 용어)
 */
data class BacktestEnhancedResponse(
    // 기본 정보
    val id: Long,
    val strategyId: Long,
    val strategyName: String?,
    val status: String,

    // 테스트 설정
    val startDate: LocalDate,
    val endDate: LocalDate,
    val initialCapital: BigDecimal,
    val finalValue: BigDecimal?,

    // 종합 평가
    val overallGrade: MetricGradeResponse,
    val summary: String,

    // 신호등 지표 (등급 포함)
    val gradedMetrics: List<GradedMetricResponse>,

    // 기존 상세 데이터 (차트/거래용)
    val equityCurve: List<EquityCurvePoint>?,
    val trades: List<BacktestTradeResponse>?,

    // 용어 사전
    val glossary: List<GlossaryTerm>,

    // 타임스탬프
    val createdAt: LocalDateTime,
    val completedAt: LocalDateTime?
)

/**
 * 등급 응답 DTO
 */
data class MetricGradeResponse(
    val grade: String,
    val label: String
) {
    companion object {
        fun from(grade: MetricGrade): MetricGradeResponse {
            return MetricGradeResponse(
                grade = grade.label,
                label = grade.labelKo
            )
        }
    }
}

/**
 * 등급이 포함된 지표 응답 DTO
 */
data class GradedMetricResponse(
    val name: String,
    val nameKo: String,
    val value: BigDecimal?,
    val formattedValue: String,
    val grade: String,
    val gradeLabel: String,
    val description: String
) {
    companion object {
        fun from(metric: GradedMetric): GradedMetricResponse {
            return GradedMetricResponse(
                name = metric.name,
                nameKo = metric.nameKo,
                value = metric.value,
                formattedValue = metric.formattedValue,
                grade = metric.grade.label,
                gradeLabel = metric.grade.labelKo,
                description = metric.description
            )
        }
    }
}

/**
 * 용어 사전 항목
 */
data class GlossaryTerm(
    val term: String,
    val termKo: String,
    val definition: String,
    val example: String? = null
)

/**
 * 투자 용어 사전 (15개)
 * PRD v2 기준
 */
object BacktestGlossary {

    val TERMS: List<GlossaryTerm> = listOf(
        GlossaryTerm(
            term = "CAGR",
            termKo = "연평균 복리 수익률",
            definition = "투자 기간 동안의 연평균 수익률입니다. 복리로 계산하여 매년 꾸준히 이 수익률을 냈다고 가정합니다.",
            example = "CAGR 15%면 매년 약 15%씩 자산이 불어난다는 뜻입니다."
        ),
        GlossaryTerm(
            term = "MDD",
            termKo = "최대 낙폭",
            definition = "투자 기간 중 가장 크게 떨어진 폭입니다. 고점 대비 최대 하락률을 나타냅니다.",
            example = "MDD -20%면 최고점에서 20% 빠진 적이 있다는 뜻입니다."
        ),
        GlossaryTerm(
            term = "Sharpe Ratio",
            termKo = "샤프 비율",
            definition = "위험 대비 얼마나 수익을 냈는지를 나타냅니다. 높을수록 같은 위험으로 더 많이 벌었다는 뜻입니다.",
            example = "샤프 비율 1.0 이상이면 일반적으로 좋은 전략으로 봅니다."
        ),
        GlossaryTerm(
            term = "Sortino Ratio",
            termKo = "소르티노 비율",
            definition = "하락 위험만 고려한 수익 효율입니다. 샤프 비율과 비슷하지만, 손실만 위험으로 봅니다.",
            example = "소르티노 비율이 높을수록 하락에 강한 전략입니다."
        ),
        GlossaryTerm(
            term = "Win Rate",
            termKo = "승률",
            definition = "전체 거래 중 수익을 낸 거래의 비율입니다.",
            example = "승률 60%면 10번 거래 중 6번 수익, 4번 손실입니다."
        ),
        GlossaryTerm(
            term = "Total Return",
            termKo = "총 수익률",
            definition = "투자 시작부터 끝까지의 전체 수익률입니다.",
            example = "1000만원 투자 후 총 수익률 50%면 500만원 수익입니다."
        ),
        GlossaryTerm(
            term = "Volatility",
            termKo = "변동성",
            definition = "수익률이 얼마나 들쭉날쭉한지를 나타냅니다. 높으면 수익과 손실의 폭이 큽니다.",
            example = "변동성 20%면 수익률이 ±20% 범위에서 자주 움직인다는 뜻입니다."
        ),
        GlossaryTerm(
            term = "Benchmark",
            termKo = "벤치마크",
            definition = "전략의 성과를 비교하는 기준입니다. 보통 시장 지수(코스피, S&P500)를 사용합니다.",
            example = "내 전략 수익률 15%, S&P500 수익률 10%면 벤치마크 대비 5% 초과 수익입니다."
        ),
        GlossaryTerm(
            term = "Alpha",
            termKo = "알파",
            definition = "벤치마크 대비 초과 수익률입니다. 양수면 시장보다 잘했다는 뜻입니다.",
            example = "알파 5%면 시장 수익률보다 5% 더 벌었습니다."
        ),
        GlossaryTerm(
            term = "Beta",
            termKo = "베타",
            definition = "시장 대비 얼마나 민감하게 움직이는지를 나타냅니다. 1이면 시장과 동일하게 움직입니다.",
            example = "베타 1.5면 시장이 10% 오를 때 약 15% 오르고, 떨어질 때도 1.5배 더 떨어집니다."
        ),
        GlossaryTerm(
            term = "Profit Factor",
            termKo = "손익비",
            definition = "총 수익을 총 손실로 나눈 값입니다. 1보다 크면 수익이 손실보다 많습니다.",
            example = "손익비 2.0이면 1원 잃을 때마다 2원을 벌었다는 뜻입니다."
        ),
        GlossaryTerm(
            term = "Equity Curve",
            termKo = "수익 곡선",
            definition = "투자 기간 동안 자산이 어떻게 변했는지 보여주는 그래프입니다.",
            example = "수익 곡선이 우상향이면 꾸준히 수익이 났다는 뜻입니다."
        ),
        GlossaryTerm(
            term = "Rebalancing",
            termKo = "리밸런싱",
            definition = "포트폴리오의 비중을 원래 목표대로 재조정하는 것입니다.",
            example = "주식 60%, 채권 40% 목표인데 주식이 올라 70%가 되면, 일부를 팔아 60%로 맞춥니다."
        ),
        GlossaryTerm(
            term = "Stop Loss",
            termKo = "손절",
            definition = "미리 정한 손실 한도에 도달하면 자동으로 파는 것입니다. 더 큰 손실을 방지합니다.",
            example = "-5% 손절이면 5% 손실 시 자동으로 매도합니다."
        ),
        GlossaryTerm(
            term = "Take Profit",
            termKo = "익절",
            definition = "미리 정한 수익 목표에 도달하면 자동으로 파는 것입니다. 수익을 확정합니다.",
            example = "+10% 익절이면 10% 수익 시 자동으로 매도합니다."
        )
    )
}
