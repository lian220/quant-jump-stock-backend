# Data Engine 리팩토링 및 확장 계획서: Docker Modular Monolith & AI

## 1. 개요 및 아키텍처 결정
현재의 분석 중심 워크로드와 **15개 이상의 전략 동시 실행**, 그리고 향후 **AI 자연어 코드 생성** 기능을 수용하기 위해 **Docker 기반 모듈러 모놀리스(Modular Monolith)** 아키텍처를 채택합니다.

## 2. 핵심 해결 전략

### 2.1 런타임 모델 (Docker Optimized)
- **상주 프로세스**: `main.py`가 무한 루프로 실행되어 DB 연결 풀을 유지(Warm Connection)하고 Kafka 메시지에 즉각 반응(Low Latency)합니다.
- **운영 효율성**: 별도의 인프라 분리 없이 단일 컨테이너 배포로 관리비용을 최소화합니다.

### 2.2 동시성 및 메모리 관리 (15+ 전략 실행 대비)
- **병렬 실행 (ThreadPool)**: 15개 이상의 전략을 순차 실행하지 않고 `ThreadPoolExecutor`를 통해 지정된 개수(예: 5개)만큼 병렬 처리하여 전체 실행 시간을 단축합니다.
- **메모리 최적화 (Shared Data)**: 
  - 각 전략이 데이터를 개별 로드하면 메모리 폭주(OOM) 위험이 있습니다.
  - **해결책**: 시장 데이터(Market Data)는 메인 프로세스에서 **단 한 번만 로드**하고, 각 전략에는 읽기 전용 참조(Reference)만 전달하여 메모리 사용량을 획기적으로 줄입니다.

### 2.3 AI 코드 생성 및 안전성 (Safety)
자연어로 생성된 코드를 실행하기 위한 안전 장치를 마련합니다.
- **엄격한 인터페이스 (`BaseStrategy`)**: 모든 전략은 `BaseStrategy`를 상속받아야 하며, `analyze(data) -> dict` 입출력 형식을 강제합니다.
- **동적 로더 (`Dynamic Loader`)**: AI가 생성한 `.py` 파일을 런타임에 동적으로 import하고, 인터페이스 준수 여부를 검증한 뒤 실행합니다.
- **샌드박싱 (Future)**: 추후 AI 전략이 불안정할 경우, 해당 실행 부분만 별도 Worker 컨테이너로 분리합니다.

## 3. 목표 디렉토리 구조

```text
src/
├── config/                     # [Phase 1] 설정 및 임계값
│   ├── settings.py             # 환경변수 (Pydantic)
│   └── thresholds.py           # RSI < 30 등 하드코딩 제거
│
├── modules/                    # [Phase 2] 핵심 비즈니스 로직
│   ├── strategy/               # 전략 엔진
│   │   ├── base.py             # [핵심] BaseStrategy 인터페이스
│   │   ├── loader.py           # [핵심] AI 코드 동적 로더
│   │   ├── engine.py           # ThreadPool 병렬 실행기
│   │   ├── strategies/         # 전략 파일 (Momentum, Value 등)
│   │   └── generated/          # AI가 생성한 코드 저장소
│   │
│   ├── technical/              # 기술적 분석 (DB 의존성 제거)
│   ├── economic/               # 경제 지표
│
├── handlers/                   # [Phase 2] 라우팅 핸들러
│   └── router.py               # Kafka 토픽 -> 모듈 매핑
│
├── shared/                     # 공통 자원
│   └── market_data.py          # [Phase 3] 공유 데이터 캐시
└── main.py                     # [Phase 2] 슬림화된 진입점
```

## 4. 단계별 이행 로드맵

### Phase 1: Foundation (기반 마련)
- **설정 분리**: `settings.py`, `thresholds.py` 작성.
- **인터페이스 정의**: `modules/strategy/base.py` 작성.

### Phase 2: Modularization (모듈화)
- 기존 `services/` 코드를 `modules/`로 이동.
- `main.py`의 로직을 `handlers/router.py`로 분리.

### Phase 3: Strategy Engine (병렬 처리)
- 15개 전략을 동시에 실행할 `engine.py` 구현.
- 메모리 공유를 위한 `SharedMarketData` 구현.

### Phase 4: AI Readiness (동적 적재)
- AI 코드를 읽어올 `loader.py` 구현.
- 자연어 -> 코드 생성 파이프라인 연동 준비.

### Phase 5: Scale Out (Worker 분리)
- 부하 증가 시 Strategy Engine을 별도 컨테이너로 분리.
