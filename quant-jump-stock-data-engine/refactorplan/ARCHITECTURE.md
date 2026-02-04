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
| **백테스트 엔진** | 높음 | 독립 모듈 필요 (10년 데이터, 동시 50개) |
| **AI 전략 생성** | 높음 | 독립 모듈 필요 (Vertex AI, 스트리밍) |
| **15개 시드 전략** | 중간 | 전략별 모듈화 가능 |
| 기술적 분석 (현재) | 중간 | 현재 구조 개선 |
| 경제 데이터 수집 (현재) | 낮음 | 현재 구조 유지 |

---

## 권장 아키텍처: Modular Monolith (가벼운 버전)

### 선택 이유
1. **현재는 단순 유지** - 과도한 엔지니어링 방지
2. **확장 대비** - 백테스트, AI 전략 추가 시 자연스럽게 확장
3. **모듈 독립성** - 나중에 별도 서비스로 분리 가능
4. **테스트 용이** - 순수 계산 함수 분리로 단위 테스트 가능

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
    ├── config/                         # 설정 관리
    │   ├── __init__.py
    │   ├── settings.py                 # 환경변수 (Pydantic Settings)
    │   └── thresholds.py               # 비즈니스 규칙 임계값
    │
    ├── modules/                        # 각 분석 모듈 (독립적)
    │   ├── __init__.py
    │   │
    │   ├── economic_data/              # 경제 데이터 수집
    │   │   ├── __init__.py
    │   │   ├── service.py              # EconomicDataService
    │   │   └── repository.py           # DB 접근
    │   │
    │   ├── technical_analysis/         # 기술적 분석
    │   │   ├── __init__.py
    │   │   ├── service.py              # TechnicalAnalysisService
    │   │   ├── indicators.py           # 순수 계산 함수 (SMA, RSI, MACD)
    │   │   └── repository.py           # DB 접근
    │   │
    │   ├── sentiment_analysis/         # 감정 분석
    │   │   ├── __init__.py
    │   │   ├── service.py              # SentimentAnalysisService
    │   │   └── repository.py
    │   │
    │   ├── recommendation/             # 종합 추천
    │   │   ├── __init__.py
    │   │   ├── service.py              # RecommendationService
    │   │   └── score_calculator.py     # 점수 계산 로직
    │   │
    │   ├── backtest/                   # 백테스트 엔진 (신규 - PRD v2)
    │   │   ├── __init__.py
    │   │   ├── service.py              # BacktestService
    │   │   ├── engine.py               # 백테스트 실행 로직
    │   │   └── metrics.py              # CAGR, MDD, Sharpe 계산
    │   │
    │   ├── strategy/                   # 전략 실행 (신규 - PRD v2)
    │   │   ├── __init__.py
    │   │   ├── service.py              # StrategyService
    │   │   ├── base.py                 # BaseStrategy 인터페이스
    │   │   └── strategies/             # 각 전략 구현
    │   │       ├── __init__.py
    │   │       ├── momentum.py         # 골든크로스, RSI 과매도, MACD
    │   │       ├── value.py            # 저PER, 고배당
    │   │       ├── asset_allocation.py # 60/40, 올웨더
    │   │       └── ml_prediction.py    # Vertex AI 예측
    │   │
    │   └── ai_strategy/                # AI 전략 생성 (신규 - PRD v2)
    │       ├── __init__.py
    │       └── service.py              # AIStrategyService (Vertex AI)
    │
    ├── handlers/                       # Kafka 메시지 핸들러
    │   ├── __init__.py
    │   ├── base.py                     # BaseHandler 인터페이스
    │   ├── economic_data.py
    │   ├── technical_analysis.py
    │   ├── sentiment_analysis.py
    │   ├── combined_analysis.py
    │   ├── backtest.py                 # (신규)
    │   └── router.py                   # 토픽 → 핸들러 라우팅
    │
    ├── shared/                         # 공통 유틸리티
    │   ├── __init__.py
    │   ├── database.py                 # MongoDB, PostgreSQL 클라이언트
    │   ├── kafka.py                    # Kafka 퍼블리셔
    │   ├── slack.py                    # Slack 알림
    │   └── exceptions.py               # 커스텀 예외
    │
    └── api/                            # REST API (상태 조회용)
        ├── __init__.py
        ├── health.py
        └── status.py
```

---

## 핵심 원칙

### 1. 각 모듈은 독립적
- 모듈 간 직접 import 최소화
- 나중에 별도 서비스로 분리 가능
- 각 모듈은 `service.py`로 진입

### 2. 순수 계산 함수 분리
- `indicators.py` - SMA, RSI, MACD (pandas 의존, DB 의존 X)
- `metrics.py` - CAGR, MDD, Sharpe (순수 계산)
- `score_calculator.py` - 점수 계산 (설정 가능)

### 3. 설정 외부화
- 모든 임계값은 `config/thresholds.py`로
- 환경변수는 `config/settings.py`로

### 4. 핸들러 패턴으로 라우팅
- main.py의 if-elif 제거
- `handlers/router.py`에서 토픽 → 핸들러 매핑

---

## 상세 구현 가이드

### 1. config/settings.py

```python
from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    # MongoDB
    MONGODB_URI: str = "mongodb://localhost:27017"
    MONGODB_DB_NAME: str = "stock_trading"

    # PostgreSQL
    POSTGRES_HOST: str = "localhost"
    POSTGRES_PORT: int = 5432
    POSTGRES_DB: str = "quantiq"
    POSTGRES_USER: str = "postgres"
    POSTGRES_PASSWORD: str = ""

    # Kafka
    KAFKA_BOOTSTRAP_SERVERS: str = "localhost:9092"
    KAFKA_CONSUMER_GROUP: str = "quantiq-data-engine"
    KAFKA_TOPIC_ECONOMIC_DATA_UPDATE_REQUEST: str = "economic.data.update.request"

    # External APIs
    FRED_API_KEY: str = ""
    ALPHA_VANTAGE_API_KEY: str = ""

    # Slack
    SLACK_BOT_TOKEN: str = ""
    SLACK_WEBHOOK_URL: str = ""

    class Config:
        env_file = ".env.local"


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
```

### 2. config/thresholds.py

```python
from pydantic_settings import BaseSettings
from decimal import Decimal


class AnalysisThresholds(BaseSettings):
    """비즈니스 규칙 임계값 - 코드 수정 없이 변경 가능"""

    # 기술적 분석
    RSI_OVERSOLD: int = 30
    RSI_OVERBOUGHT: int = 70
    RSI_BUY_THRESHOLD: int = 50       # 기존: 하드코딩 50

    # 점수 가중치
    TECHNICAL_WEIGHT: float = 0.7     # 기존: 하드코딩 0.7
    SENTIMENT_WEIGHT: float = 0.3     # 기존: 하드코딩 0.3

    # 추천 임계값
    RECOMMENDATION_THRESHOLD: float = 0.6  # 기존: 하드코딩 0.6

    # 데이터 수집
    LOOKBACK_DAYS: int = 180
    MIN_DATA_POINTS: int = 50

    class Config:
        env_prefix = "THRESHOLD_"


thresholds = AnalysisThresholds()
```

### 3. modules/technical_analysis/indicators.py

```python
"""순수 계산 함수 - DB 의존성 없음, 테스트 가능"""
import pandas as pd
import numpy as np
from typing import Tuple, Optional


def calculate_sma(series: pd.Series, period: int) -> pd.Series:
    """Simple Moving Average"""
    return series.rolling(window=period, min_periods=period).mean()


def calculate_rsi(series: pd.Series, period: int = 14) -> pd.Series:
    """Relative Strength Index"""
    delta = series.diff()
    gain = delta.where(delta > 0, 0).rolling(window=period, min_periods=period).mean()
    loss = (-delta.where(delta < 0, 0)).rolling(window=period, min_periods=period).mean()

    epsilon = 1e-10
    rs = gain / (loss + epsilon)
    rs = rs.replace([np.inf, -np.inf], np.nan)
    rsi = 100 - (100 / (1 + rs))

    return rsi.clip(0, 100)


def calculate_macd(
    series: pd.Series,
    short_period: int = 12,
    long_period: int = 26,
    signal_period: int = 9
) -> Tuple[pd.Series, pd.Series]:
    """MACD and Signal line"""
    short_ema = series.ewm(span=short_period, adjust=False).mean()
    long_ema = series.ewm(span=long_period, adjust=False).mean()
    macd = short_ema - long_ema
    signal = macd.ewm(span=signal_period, adjust=False).mean()

    return macd, signal


def is_buy_signal(
    sma20: float,
    sma50: float,
    rsi: float,
    macd: float,
    signal: float,
    rsi_threshold: int = 50
) -> bool:
    """매수 신호 판단 - 설정 가능한 임계값"""
    golden_cross = sma20 > sma50
    macd_buy = macd > signal
    rsi_ok = rsi < rsi_threshold

    return golden_cross and rsi_ok and macd_buy
```

### 4. handlers/router.py

```python
"""토픽 → 핸들러 라우팅"""
import logging
from typing import Dict, Callable, Any

from src.config.settings import settings

logger = logging.getLogger(__name__)


class MessageRouter:
    """Kafka 메시지 라우터"""

    def __init__(self):
        self._handlers: Dict[str, Callable] = {}

    def register(self, topic: str, handler: Callable) -> None:
        """핸들러 등록"""
        self._handlers[topic] = handler
        logger.info(f"Registered handler for topic: {topic}")

    def route(self, topic: str, message: dict) -> Any:
        """메시지 라우팅"""
        handler = self._handlers.get(topic)

        if handler is None:
            logger.warning(f"No handler for topic: {topic}")
            return None

        return handler(message)

    def get_topics(self) -> list:
        """등록된 토픽 목록"""
        return list(self._handlers.keys())


def create_router() -> MessageRouter:
    """라우터 생성 및 핸들러 등록"""
    from src.handlers.economic_data import handle_economic_data
    from src.handlers.technical_analysis import handle_technical_analysis
    from src.handlers.sentiment_analysis import handle_sentiment_analysis
    from src.handlers.combined_analysis import handle_combined_analysis

    router = MessageRouter()

    router.register(
        settings.KAFKA_TOPIC_ECONOMIC_DATA_UPDATE_REQUEST,
        handle_economic_data
    )
    router.register("analysis.technical.request", handle_technical_analysis)
    router.register("analysis.sentiment.request", handle_sentiment_analysis)
    router.register("analysis.combined.request", handle_combined_analysis)

    return router
```

### 5. handlers/technical_analysis.py

```python
"""기술적 분석 핸들러"""
import logging
import time
from datetime import datetime
from pytz import timezone

from src.modules.technical_analysis.service import TechnicalAnalysisService
from src.shared.kafka import KafkaEventPublisher
from src.shared.slack import SlackNotifier

logger = logging.getLogger(__name__)
KST = timezone('Asia/Seoul')


def handle_technical_analysis(message: dict) -> dict:
    """기술적 분석 요청 처리"""
    payload = message.get("payload", message)
    request_id = payload.get("requestId", "unknown")
    thread_ts = payload.get("threadTs")
    target_date = payload.get("targetDate")

    logger.info(f"[{request_id}] 기술적 분석 시작 (target_date={target_date})")

    service = TechnicalAnalysisService()
    start_time = time.time()

    try:
        result = service.analyze_stocks(target_date=target_date)
        elapsed_time = time.time() - start_time

        logger.info(f"[{request_id}] 기술적 분석 완료")

        KafkaEventPublisher.publish("ANALYSIS_TECHNICAL_COMPLETED", {
            "status": "success",
            "timestamp": datetime.now(KST).isoformat(),
            "requestId": request_id,
            "duration": elapsed_time,
            "result": result
        })

        return {"success": True, "result": result}

    except Exception as e:
        logger.error(f"[{request_id}] 기술적 분석 실패: {e}")

        KafkaEventPublisher.publish("ANALYSIS_TECHNICAL_FAILED", {
            "status": "failed",
            "timestamp": datetime.now(KST).isoformat(),
            "requestId": request_id,
            "error": str(e)
        })

        return {"success": False, "error": str(e)}
```

### 6. main.py (슬림화)

```python
"""Quantiq Data Engine - 진입점"""
import logging
import json
import time
import threading
from fastapi import FastAPI
import uvicorn
from confluent_kafka import Consumer, KafkaError

from src.config.settings import settings
from src.shared.database import MongoDB
from src.handlers.router import create_router
from src.api.health import router as health_router
from src.api.status import router as status_router

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# FastAPI
app = FastAPI(title="Quantiq Data Engine")
app.include_router(health_router)
app.include_router(status_router)


def run_api():
    uvicorn.run(app, host="0.0.0.0", port=8000)


def main():
    logger.info("Quantiq Data Engine Started")

    # API 서버 (백그라운드)
    api_thread = threading.Thread(target=run_api, daemon=True)
    api_thread.start()

    # MongoDB 연결
    db = MongoDB.get_db()
    if db is None:
        logger.error("Failed to connect to MongoDB")
        return

    # Kafka Consumer
    consumer = Consumer({
        'bootstrap.servers': settings.KAFKA_BOOTSTRAP_SERVERS,
        'group.id': settings.KAFKA_CONSUMER_GROUP,
        'auto.offset.reset': 'earliest'
    })

    # 라우터 생성 및 토픽 구독
    router = create_router()
    topics = router.get_topics()
    consumer.subscribe(topics)
    logger.info(f"Subscribed to topics: {topics}")

    # 메시지 처리 루프
    try:
        while True:
            msg = consumer.poll(1.0)

            if msg is None:
                continue
            if msg.error():
                if msg.error().code() != KafkaError._PARTITION_EOF:
                    logger.error(f"Consumer error: {msg.error()}")
                continue

            try:
                topic = msg.topic()
                message = json.loads(msg.value().decode('utf-8'))
                logger.info(f"Received message from '{topic}'")

                router.route(topic, message)

            except Exception as e:
                logger.error(f"Error processing message: {e}")

    except KeyboardInterrupt:
        pass
    finally:
        consumer.close()


if __name__ == "__main__":
    main()
```

---

## 마이그레이션 계획 (6주)

### Phase 1: Foundation (Week 1-2)

**목표**: 설정 외부화, 프로젝트 구조 정리

1. **pyproject.toml 업데이트**
   ```toml
   [tool.poetry.dependencies]
   pydantic-settings = "^2.1.0"

   [tool.poetry.group.dev.dependencies]
   pytest = "^8.0.0"
   pytest-cov = "^4.1.0"
   ```

2. `config/settings.py` 생성
3. `config/thresholds.py` 생성 - RSI < 50 등 외부화
4. `shared/exceptions.py` 생성

**변경 파일**:
- `pyproject.toml`
- 신규: `src/config/settings.py`
- 신규: `src/config/thresholds.py`
- 신규: `src/shared/exceptions.py`

### Phase 2: Handlers (Week 3)

**목표**: main.py의 if-elif 분리

1. `handlers/router.py` 생성
2. `handlers/economic_data.py` 생성
3. `handlers/technical_analysis.py` 생성
4. `handlers/sentiment_analysis.py` 생성
5. `handlers/combined_analysis.py` 생성
6. `main.py` 슬림화 (315줄 → ~80줄)

**변경 파일**:
- `src/main.py` (대폭 수정)
- 신규: `src/handlers/*.py`

### Phase 3: Modules (Week 4)

**목표**: 기존 서비스를 모듈 구조로 이동

1. `modules/economic_data/` 생성 (기존 features/economic_data 이동)
2. `modules/technical_analysis/` 생성
   - `service.py` - 기존 services/technical_analysis.py
   - `indicators.py` - 순수 계산 함수 추출
3. `modules/sentiment_analysis/` 생성
4. `modules/recommendation/` 생성
   - `score_calculator.py` - 점수 계산 로직 추출

**변경 파일**:
- 이동: `features/economic_data/` → `modules/economic_data/`
- 이동: `services/technical_analysis.py` → `modules/technical_analysis/`
- 이동: `services/sentiment_analysis.py` → `modules/sentiment_analysis/`
- 이동: `services/recommendation_service.py` → `modules/recommendation/`
- 삭제: `features/`, `services/` 디렉토리

### Phase 4: Testing (Week 5)

**목표**: 핵심 로직 테스트

1. `tests/` 디렉토리 구조 생성
2. `indicators.py` 단위 테스트 (순수 함수)
3. `score_calculator.py` 단위 테스트
4. `thresholds.py` 설정 테스트

**테스트 구조**:
```
tests/
├── conftest.py
├── unit/
│   ├── test_indicators.py
│   ├── test_score_calculator.py
│   └── test_thresholds.py
└── integration/
    └── test_handlers.py
```

### Phase 5: Future Modules (Week 6)

**목표**: PRD v2 기능을 위한 모듈 스캐폴딩

1. `modules/backtest/` 스캐폴딩
   - `service.py` - 인터페이스만
   - `metrics.py` - CAGR, MDD, Sharpe 계산 함수
2. `modules/strategy/` 스캐폴딩
   - `base.py` - BaseStrategy 인터페이스
   - `strategies/momentum.py` - 기존 로직 이동

**변경 파일**:
- 신규: `modules/backtest/` (스캐폴딩)
- 신규: `modules/strategy/` (스캐폴딩)

---

## 성공 지표

| 지표 | 현재 | 목표 |
|------|------|------|
| main.py 줄 수 | 315줄 | ~80줄 |
| 하드코딩된 임계값 | 5개+ | 0개 |
| 순수 계산 함수 테스트 | 0% | 80% |
| 모듈 독립성 | 낮음 | 높음 (분리 가능) |

---

## 검증 방법

### Phase 1-2 검증
```bash
# 설정 로드 확인
python -c "from src.config.settings import settings; print(settings.MONGODB_URI)"
python -c "from src.config.thresholds import thresholds; print(thresholds.RSI_BUY_THRESHOLD)"
```

### Phase 3-4 검증
```bash
# 테스트 실행
pytest tests/unit/ -v --cov=src/modules
```

### Phase 5 검증
```bash
# 전체 서비스 실행
python -m src.main

# Kafka 메시지 전송 테스트
# (기존과 동일하게 동작해야 함)
```

---

## 추가 의존성

```toml
# pyproject.toml 추가 사항

[tool.poetry.dependencies]
pydantic-settings = "^2.1.0"

[tool.poetry.group.dev.dependencies]
pytest = "^8.0.0"
pytest-cov = "^4.1.0"
```

---

## 핵심 파일 변경 요약

| 파일 | 변경 내용 |
|------|-----------|
| `src/main.py` | 315줄 → ~80줄 (핸들러 분리) |
| `src/services/technical_analysis.py` | `modules/` + `indicators.py`로 분리 |
| `src/services/recommendation_service.py` | `modules/` + `score_calculator.py`로 분리 |
| `src/features/economic_data/` | `modules/economic_data/`로 이동 |
| `pyproject.toml` | pydantic-settings, pytest 추가 |
| 신규: `src/config/` | settings.py, thresholds.py |
| 신규: `src/handlers/` | router.py, 각 핸들러 |
| 신규: `src/modules/` | 모듈별 디렉토리 |
| 신규: `tests/` | 단위 테스트 |
