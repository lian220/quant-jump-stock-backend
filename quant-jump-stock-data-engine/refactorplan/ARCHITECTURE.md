# Quant-Jump-Stock Data Engine 아키텍처 리팩토링 계획

## 현재 상태 분석

### 현재 구조 (점수: 4/10)
```
src/
├── main.py                    # 315줄 - 모놀리식 (Kafka Consumer + if-elif 라우팅)
├── core/                      # config, database, kafka
├── events/                    # schema, publisher
├── features/
│   ├── economic_data/         # service, repository, router, schemas
│   └── ml_package/            # router
└── services/                  # recommendation, technical_analysis, sentiment_analysis, slack_notifier
```

### 핵심 문제점

| 문제 | 위치 | 영향 |
|------|------|------|
| 모놀리식 이벤트 핸들러 | `main.py:110-302` | 192줄 if-elif 라우팅 |
| 하드코딩된 비즈니스 규칙 | `technical_analysis.py:132` | RSI < 50, 0.7/0.3 가중치 |
| 서비스에서 직접 DB 접근 | `technical_analysis.py:35,41` | 테스트 어려움 |
| 테스트 없음 | 전체 | 0% 커버리지 |

---

## PRD v2 분석: 향후 확장 예정 기능

| 기능 | 복잡도 | 분리 필요성 |
|------|--------|-------------|
| **다중 전략 동시 실행** | 높음 | ThreadPoolExecutor 필요 |
| **자연어 → 전략 생성** | 높음 | DSL 기반 안전한 변환 필요 |
| **백테스트 엔진** | 높음 | 독립 모듈 필요 (10년 데이터, 동시 50개) |
| **15개 시드 전략** | 중간 | Strategy DSL로 정의 |
| 기술적 분석 (현재) | 중간 | 현재 구조 개선 |
| 경제 데이터 수집 (현재) | 낮음 | 현재 구조 유지 |

---

## 권장 아키텍처: Hexagonal + Strategy DSL

### 선택 이유
1. **다중 전략 동시 실행** - ThreadPoolExecutor(max_workers=10)로 병렬 처리
2. **안전한 전략 생성** - DSL 기반으로 exec/eval 없이 안전한 실행
3. **자연어 → DSL 변환** - Vertex AI가 JSON DSL 생성 (코드 생성 X)
4. **테스트 용이** - 도메인 로직 분리로 단위 테스트 가능
5. **확장성** - 새 전략 추가 시 DSL 정의만 추가

### 제안 디렉토리 구조

```
quant-jump-stock-data-engine/
├── pyproject.toml
├── pytest.ini
│
└── src/
    ├── __init__.py
    ├── main.py                         # 진입점 (슬림화, ~80줄)
    │
    ├── domain/                         # 핵심 비즈니스 로직 (외부 의존성 없음)
    │   ├── __init__.py
    │   │
    │   ├── strategy/                   # 전략 도메인
    │   │   ├── __init__.py
    │   │   ├── models.py               # StrategyDefinition, Rule, Condition (DSL 스키마)
    │   │   ├── interpreter.py          # StrategyInterpreter (DSL 실행기)
    │   │   └── indicators.py           # 순수 계산 함수 (SMA, RSI, MACD)
    │   │
    │   ├── backtest/                   # 백테스트 도메인
    │   │   ├── __init__.py
    │   │   ├── models.py               # BacktestResult, PerformanceMetrics
    │   │   └── metrics.py              # CAGR, MDD, Sharpe 계산
    │   │
    │   ├── analysis/                   # 분석 도메인
    │   │   ├── __init__.py
    │   │   ├── models.py               # AnalysisResult, TechnicalSignal
    │   │   └── score_calculator.py     # 점수 계산 로직
    │   │
    │   └── common/                     # 공통 도메인 모델
    │       ├── __init__.py
    │       ├── value_objects.py        # StockCode, DateRange 등
    │       └── exceptions.py           # 도메인 예외
    │
    ├── application/                    # 유스케이스 (오케스트레이션)
    │   ├── __init__.py
    │   │
    │   ├── strategy/                   # 전략 유스케이스
    │   │   ├── __init__.py
    │   │   ├── executor.py             # StrategyExecutor (ThreadPoolExecutor)
    │   │   ├── generator.py            # StrategyGenerator (자연어 → DSL)
    │   │   └── service.py              # StrategyService
    │   │
    │   ├── backtest/                   # 백테스트 유스케이스
    │   │   ├── __init__.py
    │   │   └── service.py              # BacktestService
    │   │
    │   ├── analysis/                   # 분석 유스케이스
    │   │   ├── __init__.py
    │   │   ├── technical.py            # TechnicalAnalysisService
    │   │   ├── sentiment.py            # SentimentAnalysisService
    │   │   └── combined.py             # CombinedAnalysisService
    │   │
    │   ├── economic/                   # 경제 데이터 유스케이스
    │   │   ├── __init__.py
    │   │   └── service.py              # EconomicDataService
    │   │
    │   └── ports/                      # 포트 인터페이스 (추상화)
    │       ├── __init__.py
    │       ├── repositories.py         # IStockRepository, IStrategyRepository
    │       ├── external.py             # IVertexAIClient, IKISClient
    │       └── messaging.py            # IEventPublisher
    │
    ├── adapters/                       # 어댑터 (외부 연동)
    │   ├── __init__.py
    │   │
    │   ├── inbound/                    # 인바운드 어댑터
    │   │   ├── __init__.py
    │   │   ├── kafka/                  # Kafka Consumer
    │   │   │   ├── __init__.py
    │   │   │   ├── consumer.py
    │   │   │   ├── router.py           # 토픽 → 핸들러 라우팅
    │   │   │   └── handlers/           # 메시지 핸들러
    │   │   │       ├── __init__.py
    │   │   │       ├── strategy.py
    │   │   │       ├── backtest.py
    │   │   │       ├── analysis.py
    │   │   │       └── economic.py
    │   │   │
    │   │   └── api/                    # REST API
    │   │       ├── __init__.py
    │   │       ├── health.py
    │   │       └── status.py
    │   │
    │   └── outbound/                   # 아웃바운드 어댑터
    │       ├── __init__.py
    │       ├── persistence/            # 데이터베이스
    │       │   ├── __init__.py
    │       │   ├── mongodb/            # MongoDB 어댑터
    │       │   │   ├── __init__.py
    │       │   │   ├── stock_repository.py
    │       │   │   └── strategy_repository.py
    │       │   └── postgres/           # PostgreSQL 어댑터
    │       │       ├── __init__.py
    │       │       └── config_repository.py
    │       │
    │       ├── external/               # 외부 API
    │       │   ├── __init__.py
    │       │   ├── vertex_ai.py        # Vertex AI 클라이언트
    │       │   ├── kis.py              # 한국투자증권 API
    │       │   └── fred.py             # FRED API
    │       │
    │       ├── messaging/              # 메시징
    │       │   ├── __init__.py
    │       │   └── kafka_publisher.py
    │       │
    │       └── notification/           # 알림
    │           ├── __init__.py
    │           └── slack.py
    │
    └── config/                         # 설정
        ├── __init__.py
        ├── settings.py                 # 환경변수 (Pydantic Settings)
        └── thresholds.py               # 비즈니스 규칙 임계값
```

---

## 핵심 구현: Strategy DSL

### 1. domain/strategy/models.py (DSL 스키마)

```python
"""Strategy DSL 스키마 - JSON으로 전략 정의"""
from pydantic import BaseModel, Field
from typing import List, Literal, Optional, Dict, Any
from enum import Enum


class IndicatorType(str, Enum):
    SMA = "sma"
    RSI = "rsi"
    MACD = "macd"
    BOLLINGER = "bollinger"
    VOLUME = "volume"


class ConditionOperator(str, Enum):
    GREATER_THAN = "gt"
    LESS_THAN = "lt"
    EQUALS = "eq"
    CROSSES_ABOVE = "crosses_above"
    CROSSES_BELOW = "crosses_below"


class Condition(BaseModel):
    """단일 조건"""
    indicator: IndicatorType
    params: Dict[str, Any] = Field(default_factory=dict)  # {"period": 20}
    operator: ConditionOperator
    value: float | str  # 숫자 또는 다른 지표 참조 ("sma_50")


class Rule(BaseModel):
    """매수/매도 규칙"""
    name: str
    signal_type: Literal["buy", "sell"]
    conditions: List[Condition]
    logic: Literal["and", "or"] = "and"
    weight: float = 1.0


class RiskManagement(BaseModel):
    """리스크 관리"""
    stop_loss_pct: float = 0.05           # 5% 손절
    take_profit_pct: float = 0.15         # 15% 익절
    max_position_pct: float = 0.1         # 포트폴리오 10% 한도
    max_drawdown_pct: float = 0.2         # 최대 낙폭 20%


class StrategyDefinition(BaseModel):
    """전략 정의 (DSL 최상위)"""
    strategy_id: str
    name: str
    description: str = ""
    version: str = "1.0"

    # 전략 규칙
    rules: List[Rule]

    # 리스크 관리
    risk_management: RiskManagement = Field(default_factory=RiskManagement)

    # 메타데이터
    is_ai_generated: bool = False
    source_prompt: Optional[str] = None  # 자연어 원본 (AI 생성 시)
    created_at: Optional[str] = None

    class Config:
        json_schema_extra = {
            "example": {
                "strategy_id": "golden_cross_v1",
                "name": "Golden Cross Strategy",
                "rules": [
                    {
                        "name": "golden_cross_buy",
                        "signal_type": "buy",
                        "conditions": [
                            {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_above", "value": "sma_50"},
                            {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 70}
                        ],
                        "logic": "and"
                    }
                ],
                "risk_management": {"stop_loss_pct": 0.05}
            }
        }
```

### 2. domain/strategy/interpreter.py (DSL 실행기)

```python
"""Strategy Interpreter - DSL을 안전하게 실행 (exec/eval 없음)"""
import logging
from typing import Dict, List, Optional
import pandas as pd

from .models import StrategyDefinition, Rule, Condition, ConditionOperator, IndicatorType
from .indicators import calculate_sma, calculate_rsi, calculate_macd, calculate_bollinger

logger = logging.getLogger(__name__)


class StrategyInterpreter:
    """
    DSL 기반 전략 해석기
    - exec/eval 사용 안함 (보안)
    - 사전 정의된 지표 함수만 사용
    - 조건 평가는 딕셔너리 기반
    """

    def __init__(self):
        # 허용된 지표 함수 매핑
        self._indicator_functions = {
            IndicatorType.SMA: calculate_sma,
            IndicatorType.RSI: calculate_rsi,
            IndicatorType.MACD: calculate_macd,
            IndicatorType.BOLLINGER: calculate_bollinger,
        }

        # 연산자 함수 매핑
        self._operators = {
            ConditionOperator.GREATER_THAN: lambda a, b: a > b,
            ConditionOperator.LESS_THAN: lambda a, b: a < b,
            ConditionOperator.EQUALS: lambda a, b: abs(a - b) < 0.0001,
            ConditionOperator.CROSSES_ABOVE: self._crosses_above,
            ConditionOperator.CROSSES_BELOW: self._crosses_below,
        }

    def execute(
        self,
        strategy: StrategyDefinition,
        market_data: pd.DataFrame
    ) -> Dict:
        """
        전략 실행

        Args:
            strategy: DSL 기반 전략 정의
            market_data: OHLCV 데이터 (columns: open, high, low, close, volume)

        Returns:
            {
                "signals": [{"date": "2024-01-15", "type": "buy", "rule": "golden_cross", "confidence": 0.85}],
                "indicators": {"sma_20": [...], "rsi_14": [...]},
                "risk_checks": {"stop_loss": False, "take_profit": False}
            }
        """
        # 1. 모든 지표 계산
        indicators = self._calculate_all_indicators(strategy.rules, market_data)

        # 2. 각 규칙 평가
        signals = []
        for rule in strategy.rules:
            signal = self._evaluate_rule(rule, indicators, market_data)
            if signal:
                signals.append(signal)

        # 3. 리스크 체크
        risk_checks = self._check_risk_management(
            strategy.risk_management,
            market_data,
            signals
        )

        return {
            "strategy_id": strategy.strategy_id,
            "signals": signals,
            "indicators": {k: v.tolist() if hasattr(v, 'tolist') else v for k, v in indicators.items()},
            "risk_checks": risk_checks
        }

    def _calculate_all_indicators(
        self,
        rules: List[Rule],
        data: pd.DataFrame
    ) -> Dict[str, pd.Series]:
        """모든 필요한 지표 계산"""
        indicators = {}
        close = data['close']

        for rule in rules:
            for condition in rule.conditions:
                key = f"{condition.indicator.value}_{condition.params.get('period', '')}"

                if key not in indicators:
                    func = self._indicator_functions.get(condition.indicator)
                    if func:
                        indicators[key] = func(close, **condition.params)

        return indicators

    def _evaluate_rule(
        self,
        rule: Rule,
        indicators: Dict[str, pd.Series],
        data: pd.DataFrame
    ) -> Optional[Dict]:
        """단일 규칙 평가"""
        results = []

        for condition in rule.conditions:
            key = f"{condition.indicator.value}_{condition.params.get('period', '')}"
            indicator_value = indicators.get(key)

            if indicator_value is None or indicator_value.empty:
                continue

            # 마지막 값 사용
            current_value = indicator_value.iloc[-1]

            # 비교 대상 값 결정
            if isinstance(condition.value, str) and condition.value in indicators:
                compare_value = indicators[condition.value].iloc[-1]
            else:
                compare_value = float(condition.value)

            # 연산자 적용
            operator_func = self._operators.get(condition.operator)
            if operator_func:
                # crosses_above/below는 시리즈 필요
                if condition.operator in [ConditionOperator.CROSSES_ABOVE, ConditionOperator.CROSSES_BELOW]:
                    if isinstance(condition.value, str):
                        compare_series = indicators.get(condition.value)
                    else:
                        compare_series = pd.Series([condition.value] * len(indicator_value))
                    result = operator_func(indicator_value, compare_series)
                else:
                    result = operator_func(current_value, compare_value)
                results.append(result)

        # 논리 연산
        if not results:
            return None

        if rule.logic == "and":
            passed = all(results)
        else:
            passed = any(results)

        if passed:
            return {
                "date": data.index[-1].isoformat() if hasattr(data.index[-1], 'isoformat') else str(data.index[-1]),
                "type": rule.signal_type,
                "rule": rule.name,
                "confidence": rule.weight
            }

        return None

    def _crosses_above(self, series1: pd.Series, series2: pd.Series) -> bool:
        """교차 상향 체크"""
        if len(series1) < 2:
            return False
        return series1.iloc[-2] <= series2.iloc[-2] and series1.iloc[-1] > series2.iloc[-1]

    def _crosses_below(self, series1: pd.Series, series2: pd.Series) -> bool:
        """교차 하향 체크"""
        if len(series1) < 2:
            return False
        return series1.iloc[-2] >= series2.iloc[-2] and series1.iloc[-1] < series2.iloc[-1]

    def _check_risk_management(
        self,
        risk: 'RiskManagement',
        data: pd.DataFrame,
        signals: List[Dict]
    ) -> Dict:
        """리스크 관리 체크"""
        if data.empty:
            return {"stop_loss": False, "take_profit": False}

        # 간단한 구현 - 실제로는 포지션 추적 필요
        return {
            "stop_loss_triggered": False,
            "take_profit_triggered": False,
            "max_drawdown_ok": True
        }
```

### 3. application/strategy/executor.py (동시 실행)

```python
"""Strategy Executor - 다중 전략 동시 실행"""
import logging
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import List, Dict
import pandas as pd

from src.domain.strategy.models import StrategyDefinition
from src.domain.strategy.interpreter import StrategyInterpreter
from src.application.ports.repositories import IStockRepository

logger = logging.getLogger(__name__)


class StrategyExecutor:
    """
    다중 전략 동시 실행기
    - ThreadPoolExecutor로 병렬 처리
    - 각 전략은 독립적으로 실행
    - 결과 집계 및 우선순위 정렬
    """

    def __init__(
        self,
        interpreter: StrategyInterpreter,
        stock_repository: IStockRepository,
        max_workers: int = 10
    ):
        self._interpreter = interpreter
        self._stock_repository = stock_repository
        self._executor = ThreadPoolExecutor(max_workers=max_workers)

    def execute_strategies(
        self,
        strategies: List[StrategyDefinition],
        stock_codes: List[str],
        start_date: str,
        end_date: str
    ) -> Dict:
        """
        다중 전략 동시 실행

        Args:
            strategies: 실행할 전략 목록
            stock_codes: 분석할 종목 코드 목록
            start_date: 시작일
            end_date: 종료일

        Returns:
            {
                "results": [
                    {"strategy_id": "...", "stock_code": "...", "signals": [...]},
                    ...
                ],
                "summary": {"total_signals": 10, "buy_signals": 6, "sell_signals": 4},
                "errors": []
            }
        """
        futures = []
        results = []
        errors = []

        # 전략 x 종목 조합으로 병렬 실행
        for strategy in strategies:
            for stock_code in stock_codes:
                future = self._executor.submit(
                    self._execute_single,
                    strategy,
                    stock_code,
                    start_date,
                    end_date
                )
                futures.append((future, strategy.strategy_id, stock_code))

        # 결과 수집
        for future, strategy_id, stock_code in futures:
            try:
                result = future.result(timeout=60)
                result["strategy_id"] = strategy_id
                result["stock_code"] = stock_code
                results.append(result)
            except Exception as e:
                logger.error(f"Strategy execution failed: {strategy_id}/{stock_code} - {e}")
                errors.append({
                    "strategy_id": strategy_id,
                    "stock_code": stock_code,
                    "error": str(e)
                })

        # 요약 생성
        summary = self._create_summary(results)

        return {
            "results": results,
            "summary": summary,
            "errors": errors
        }

    def _execute_single(
        self,
        strategy: StrategyDefinition,
        stock_code: str,
        start_date: str,
        end_date: str
    ) -> Dict:
        """단일 전략-종목 실행"""
        # 시장 데이터 조회
        market_data = self._stock_repository.get_ohlcv(
            stock_code, start_date, end_date
        )

        if market_data.empty:
            return {"signals": [], "indicators": {}, "risk_checks": {}}

        # 전략 실행
        return self._interpreter.execute(strategy, market_data)

    def _create_summary(self, results: List[Dict]) -> Dict:
        """결과 요약"""
        all_signals = []
        for r in results:
            all_signals.extend(r.get("signals", []))

        buy_signals = [s for s in all_signals if s.get("type") == "buy"]
        sell_signals = [s for s in all_signals if s.get("type") == "sell"]

        return {
            "total_strategies": len(set(r.get("strategy_id") for r in results)),
            "total_stocks": len(set(r.get("stock_code") for r in results)),
            "total_signals": len(all_signals),
            "buy_signals": len(buy_signals),
            "sell_signals": len(sell_signals)
        }

    def shutdown(self):
        """Executor 종료"""
        self._executor.shutdown(wait=True)
```

### 4. application/strategy/generator.py (자연어 → DSL)

```python
"""Strategy Generator - 자연어를 DSL로 변환"""
import logging
import json
from typing import Optional

from src.domain.strategy.models import StrategyDefinition
from src.application.ports.external import IVertexAIClient

logger = logging.getLogger(__name__)


class StrategyGenerator:
    """
    자연어 → Strategy DSL 변환
    - Vertex AI가 JSON DSL 생성 (코드 아님)
    - 생성된 DSL 검증
    - 안전한 실행 보장
    """

    DSL_GENERATION_PROMPT = '''
당신은 퀀트 투자 전략 DSL 생성기입니다.
사용자의 자연어 설명을 아래 JSON 스키마에 맞는 전략 DSL로 변환하세요.

## 사용 가능한 지표
- sma: 단순이동평균 (params: period)
- rsi: RSI (params: period, 기본 14)
- macd: MACD (params: short_period=12, long_period=26, signal_period=9)
- bollinger: 볼린저밴드 (params: period=20, std=2)

## 사용 가능한 연산자
- gt: 초과 (greater than)
- lt: 미만 (less than)
- eq: 같음 (equals)
- crosses_above: 상향 돌파
- crosses_below: 하향 돌파

## 출력 형식 (JSON)
{
    "strategy_id": "unique_id",
    "name": "전략 이름",
    "description": "전략 설명",
    "rules": [
        {
            "name": "규칙 이름",
            "signal_type": "buy" | "sell",
            "conditions": [
                {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_above", "value": "sma_50"}
            ],
            "logic": "and" | "or"
        }
    ],
    "risk_management": {
        "stop_loss_pct": 0.05,
        "take_profit_pct": 0.15
    }
}

## 사용자 요청
{user_prompt}

## JSON DSL 출력:
'''

    def __init__(self, vertex_ai_client: IVertexAIClient):
        self._vertex_ai = vertex_ai_client

    async def generate(self, user_prompt: str) -> Optional[StrategyDefinition]:
        """
        자연어를 전략 DSL로 변환

        Args:
            user_prompt: 사용자의 자연어 전략 설명

        Returns:
            StrategyDefinition or None
        """
        try:
            # 1. Vertex AI에 DSL 생성 요청
            prompt = self.DSL_GENERATION_PROMPT.format(user_prompt=user_prompt)
            response = await self._vertex_ai.generate_text(prompt)

            # 2. JSON 파싱
            json_str = self._extract_json(response)
            dsl_dict = json.loads(json_str)

            # 3. DSL 검증 및 객체 생성
            strategy = StrategyDefinition(**dsl_dict)
            strategy.is_ai_generated = True
            strategy.source_prompt = user_prompt

            logger.info(f"Generated strategy: {strategy.strategy_id}")
            return strategy

        except Exception as e:
            logger.error(f"Strategy generation failed: {e}")
            return None

    def _extract_json(self, text: str) -> str:
        """응답에서 JSON 부분 추출"""
        # ```json ... ``` 블록 찾기
        if "```json" in text:
            start = text.find("```json") + 7
            end = text.find("```", start)
            return text[start:end].strip()

        # { } 블록 찾기
        start = text.find("{")
        end = text.rfind("}") + 1
        return text[start:end]
```

---

## DSL 예시

### 골든크로스 전략

```json
{
    "strategy_id": "golden_cross_v1",
    "name": "Golden Cross Strategy",
    "description": "20일선이 50일선을 상향 돌파하고 RSI가 과매수가 아닐 때 매수",
    "version": "1.0",
    "rules": [
        {
            "name": "golden_cross_buy",
            "signal_type": "buy",
            "conditions": [
                {
                    "indicator": "sma",
                    "params": {"period": 20},
                    "operator": "crosses_above",
                    "value": "sma_50"
                },
                {
                    "indicator": "rsi",
                    "params": {"period": 14},
                    "operator": "lt",
                    "value": 70
                }
            ],
            "logic": "and",
            "weight": 1.0
        },
        {
            "name": "death_cross_sell",
            "signal_type": "sell",
            "conditions": [
                {
                    "indicator": "sma",
                    "params": {"period": 20},
                    "operator": "crosses_below",
                    "value": "sma_50"
                }
            ],
            "logic": "and",
            "weight": 1.0
        }
    ],
    "risk_management": {
        "stop_loss_pct": 0.05,
        "take_profit_pct": 0.15,
        "max_position_pct": 0.1
    },
    "is_ai_generated": false
}
```

### RSI 과매도 전략

```json
{
    "strategy_id": "rsi_oversold_v1",
    "name": "RSI Oversold Strategy",
    "description": "RSI가 30 이하로 과매도 상태일 때 매수",
    "rules": [
        {
            "name": "rsi_oversold_buy",
            "signal_type": "buy",
            "conditions": [
                {
                    "indicator": "rsi",
                    "params": {"period": 14},
                    "operator": "lt",
                    "value": 30
                }
            ],
            "logic": "and"
        },
        {
            "name": "rsi_overbought_sell",
            "signal_type": "sell",
            "conditions": [
                {
                    "indicator": "rsi",
                    "params": {"period": 14},
                    "operator": "gt",
                    "value": 70
                }
            ],
            "logic": "and"
        }
    ],
    "risk_management": {
        "stop_loss_pct": 0.03,
        "take_profit_pct": 0.1
    }
}
```

---

## 마이그레이션 계획 (10주)

### Phase 1: Foundation (Week 1-2)

**목표**: 설정 외부화, 도메인 모델 정의

1. `config/settings.py`, `config/thresholds.py` 생성
2. `domain/strategy/models.py` - DSL 스키마 정의
3. `domain/strategy/indicators.py` - 순수 계산 함수
4. `domain/common/exceptions.py` - 도메인 예외

**변경 파일**:
- 신규: `src/config/settings.py`
- 신규: `src/config/thresholds.py`
- 신규: `src/domain/strategy/models.py`
- 신규: `src/domain/strategy/indicators.py`
- 신규: `src/domain/common/exceptions.py`

### Phase 2: Strategy Core (Week 3-4)

**목표**: DSL 인터프리터 구현

1. `domain/strategy/interpreter.py` - DSL 실행기
2. 시드 전략 DSL 파일 작성 (15개)
3. 인터프리터 단위 테스트

**변경 파일**:
- 신규: `src/domain/strategy/interpreter.py`
- 신규: `src/domain/strategy/seed_strategies/*.json`
- 신규: `tests/unit/test_interpreter.py`

### Phase 3: Application Layer (Week 5-6)

**목표**: 유스케이스 및 포트 정의

1. `application/ports/` - 인터페이스 정의
2. `application/strategy/executor.py` - ThreadPoolExecutor
3. `application/strategy/service.py` - 전략 서비스
4. 기존 분석 서비스 이동 (`application/analysis/`)

**변경 파일**:
- 신규: `src/application/ports/*.py`
- 신규: `src/application/strategy/*.py`
- 이동: `services/` → `application/analysis/`

### Phase 4: Adapters (Week 7-8)

**목표**: 어댑터 구현

1. `adapters/inbound/kafka/` - 핸들러 분리
2. `adapters/outbound/persistence/` - 레포지토리 구현
3. `adapters/outbound/external/` - 외부 API 클라이언트
4. main.py 슬림화 (315줄 → ~80줄)

**변경 파일**:
- 신규: `src/adapters/inbound/kafka/*.py`
- 신규: `src/adapters/outbound/persistence/*.py`
- 신규: `src/adapters/outbound/external/*.py`
- 수정: `src/main.py` (대폭 수정)

### Phase 5: AI Integration (Week 9)

**목표**: 자연어 → DSL 변환

1. `application/strategy/generator.py` - Vertex AI 연동
2. Vertex AI 프롬프트 최적화
3. 생성된 DSL 검증 로직

**변경 파일**:
- 신규: `src/application/strategy/generator.py`
- 신규: `src/adapters/outbound/external/vertex_ai.py`

### Phase 6: Testing & Polish (Week 10)

**목표**: 테스트 및 통합

1. 통합 테스트 작성
2. 기존 기능 회귀 테스트
3. 문서화

**테스트 구조**:
```
tests/
├── conftest.py
├── unit/
│   ├── test_indicators.py
│   ├── test_interpreter.py
│   └── test_executor.py
└── integration/
    ├── test_strategy_flow.py
    └── test_kafka_handlers.py
```

---

## 성공 지표

| 지표 | 현재 | 목표 |
|------|------|------|
| main.py 줄 수 | 315줄 | ~80줄 |
| 하드코딩된 임계값 | 5개+ | 0개 |
| exec/eval 사용 | N/A | 0개 (DSL 기반) |
| 동시 실행 전략 수 | 1개 | 10개+ |
| 순수 계산 함수 테스트 | 0% | 80% |
| 자연어 → 전략 변환 | 불가 | 가능 (DSL) |

---

## 핵심 원칙

### 1. 안전한 전략 실행
- **exec/eval 금지** - DSL 인터프리터만 사용
- 허용된 지표 함수만 실행
- 모든 입력은 Pydantic으로 검증

### 2. 도메인 분리
- `domain/` - 비즈니스 로직 (외부 의존성 없음)
- `application/` - 유스케이스 오케스트레이션
- `adapters/` - 외부 시스템 연동

### 3. 테스트 가능성
- 순수 함수로 지표 계산
- 포트 인터페이스로 모킹 가능
- DSL 기반으로 전략 테스트 용이

### 4. 확장성
- 새 지표: `domain/strategy/indicators.py`에 함수 추가
- 새 전략: JSON DSL 파일 추가
- 새 어댑터: `adapters/` 하위에 구현

---

## 검증 방법

### Phase 1-2 검증
```bash
# DSL 파싱 테스트
python -c "from src.domain.strategy.models import StrategyDefinition; print('OK')"

# 지표 계산 테스트
pytest tests/unit/test_indicators.py -v
```

### Phase 3-4 검증
```bash
# 전략 실행 테스트
pytest tests/unit/test_interpreter.py -v

# 동시 실행 테스트
pytest tests/unit/test_executor.py -v
```

### Phase 5-6 검증
```bash
# 전체 통합 테스트
pytest tests/ -v --cov=src

# 기존 기능 회귀 테스트
python -m src.main  # Kafka 메시지 처리 확인
```
