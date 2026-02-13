# 전략 비교 및 다중 벤치마크 지원 (Multi-Benchmark Support)

전략의 성과를 시장 지수(Index)뿐만 아니라 타 전략과도 동시에 비교할 수 있도록 개선합니다.

## 1. 개요 (Overview)
- **목적**: 단일 벤치마크 비교 구조를 확장하여 여러 지수 및 타 전략과의 상대적 성과를 통합 차트에서 제공.
- **운영 정책**:
    - **다중 비교 제한**: 시스템 부하 및 가독성을 위해 **최대 3개**까지 선택 가능.
    - **비교 대상**:
        - **핵심 지수**: S&P 500, NASDAQ, KOSPI, KOSDAQ (신규 추가).
        - **타 전략**: 마켓플레이스 공개 전략 혹은 본인 소유 전략.
    - **권한 제한**: 
        - **일반(FREE) 유저**: 시장 지수 및 ETF만 벤치마크로 선택 가능.
        - **프리미엄(PREMIUM) 유저**: 시장 지수 + **타 전략 간 비교** 가능.
    - **정규화**: 모든 시계열 데이터는 시작일 기준 초기 자본금으로 정합하여 표시.

---

## 2. 데이터 모델 및 API 변경 (Schema & API)

### 2.1 벤치마크 타입 (`BenchmarkType.kt`)
```kotlin
enum class BenchmarkType(val value: String) {
    ETF("etf"),
    INDEX("index"),
    STRATEGY("strategy") // 타 전략 비교용 추가
}
```

### 2.2 요청 파라미터 (`BacktestRunRequest.kt`)
```kotlin
data class BacktestRunRequest(
    val strategyId: Long,
    /**
     * 다중 벤치마크 리스트 (최대 3개)
     * 예: ["^GSPC", "^KS11", "STRATEGY:105"]
     */
    val benchmarks: List<String> = listOf("^GSPC")
)
```

---

## 3. 세부 구현 로직 (Implementation Details)

### 3.1 전략 간 비교 (Strategy Comparison) 규칙
- **데이터 소스**: 비교 대상 전략의 **'가장 최근 성공한(Published) 백테스트'**의 Equity Curve를 사용.
- **날짜 정합 (Date Alignment)**:
    - 현재 테스트 기간과 비교 대상 전략의 결과 기간이 다를 경우, 교집합 구간만 차트에 표시.
    - 데이터가 없는 구간은 벤치마크 라인을 끊거나 표시하지 않음 (미래 데이터 참조 방지).
- **접근 권한**: 마켓플레이스에 '공개(Public)'된 전략 또는 본인이 생성한 전략만 비교 대상으로 선택 가능.

### 3.2 핵심 지수 데이터 보완
- **KOSPI/KOSDAQ 지원**: `BenchmarkRepository.kt`에 아래 티커 추가 및 MongoDB 수집 프로세스 확인.
    - KOSPI: `^KS11`
    - KOSDAQ: `^KQ11`

### 3.3 백엔드 서비스 (`BacktestService`)
- 사용자가 보낸 벤치마크 리스트의 유효성 검증 (3개 초과 시 예외 처리).
- **권한 검증**: `UserTierService`를 연동하여 `FREE` 티어 사용자가 전략(`STRATEGY:ID`)을 벤치마크에 포함했는지 체크 및 차단.
- `BenchmarkService`를 통해 각 티커/전략 ID별 시계열 데이터를 병렬 또는 순차 로드 후 통합하여 결과 전송.

---

## 4. 기대 효과
1.  **객관적 평가**: 시장 지수 대비 수익률뿐만 아니라 경쟁 전략 대비 우위성 시각화.
2.  **수익 모델 연계**: '전략 간 비교'를 프리미엄 전용 기능으로 제공하여 유료 구독 가치 증대.
3.  **데이터 기반 의사결정**: 기존 운용 전략보다 나은 성과인지 즉각적인 대조 가능.
3.  **플랫폼 신뢰도**: 다양한 비교 지표 제공으로 분석 기능의 전문성 확보.
