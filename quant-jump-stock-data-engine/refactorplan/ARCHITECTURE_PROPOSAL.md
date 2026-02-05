# 데이터 엔진 구조 분석 및 제안서

작성자: ChatGPT (Codex)
작성일: 2026-02-05
대상: quant-jump-stock-data-engine

## 1. 현재 아키텍처 분석

### 구조 개요
현재 `quant-jump-stock-data-engine`은 **메시지 기반 모놀리스(Message-Driven Monolith)** 형태입니다.
- **진입점(Entry Point)**: `main.py`가 애플리케이션의 시작점이자 메시지 분배기(Dispatcher) 역할을 동시에 수행합니다.
- **레이어 구성**:
    - `features/`: 비즈니스 로직 포함 (예: `economic_data`).
    - `services/`: 보조 서비스 포함 (`recommendation`, `sentiment`, `technical`).
    - `core/`: 인프라 설정 (`database`, `config`, `kafka`).
- **통신 방식**: 입력은 Kafka에 의존하며, 출력은 범용 `KafkaEventPublisher`를 사용합니다.

### 식별된 주요 문제점
1.  **강한 결합도 (Tight Coupling)**:
    - 서비스가 의존성을 직접 생성합니다.
    - 예: `EconomicDataService` 내부에서 `self.repository = EconomicDataRepository()`를 직접 호출.
    - **영향**: 실제 데이터베이스 없이는 `EconomicDataService`를 단위 테스트할 수 없습니다. 구현체 교체가 불가능합니다.

2.  **`main.py`의 역할 혼재**:
    - `main()` 함수가 다음 모든 기능을 처리합니다:
        - 스레드 관리 (API vs Worker).
        - 데이터베이스 연결 수명 주기.
        - Kafka 컨슈머 설정.
        - 메시지 폴링 루프.
        - **비즈니스 로직 분배**: 토픽 이름을 확인하고 페이로드를 파싱하는 거대한 `if/elif` 블록이 존재합니다.
    - **영향**: 기능이 추가될 때마다 `main.py`가 비대해집니다. 가독성과 유지보수성이 떨어집니다.

3.  **일관성 없는 레이어링**:
    - 어떤 로직은 `features/`에 있고(Economic), 어떤 것은 `services/`에 있어(Recommendation) 구분이 모호합니다.
    - `RecommendationService`가 다른 서비스(`Technical`, `Sentiment`)를 직접 오케스트레이션합니다.

4.  **암시적 의존성**:
    - 서비스들이 전역 `settings`나 싱글톤 `DB` 연결 객체의 존재를 가정하고 작성되어 있습니다.

---

## 2. 제안 아키텍처: 육각형 아키텍처 (Hexagonal Architecture / Ports & Adapters)

가장 효율적이고 유연하며 의존성이 없는 구조를 위해 **육각형 아키텍처**를 제안합니다.
이 구조는 **도메인(비즈니스 로직)**을 가장 안쪽에 배치하여 외부의 어떤 것에도 의존하지 않도록 합니다.

### 핵심 개념
1.  **도메인 레이어 (Domain / Core)**: 순수 Python 객체(Entity, Value Object). 프레임워크(FastAPI, Pandas 등)나 SQL에 의존하지 않습니다.
2.  **애플리케이션 레이어 (Application / Use Cases)**: 도메인 객체를 조작하여 업무 흐름을 관장합니다. 필요한 외부 요소(예: 리포지토리)를 인터페이스(Port)로 정의합니다.
3.  **어댑터 레이어 (Adapters / Infrastructure)**: 인터페이스의 실제 구현체입니다.
    -   **Driving Adapters (In)**: Kafka Consumer, FastAPI Controller, CLI (애플리케이션을 호출).
    -   **Driven Adapters (Out)**: MongoDB Repository, AlphaVantageClient, SlackNotifier (애플리케이션에 의해 호출됨).
4.  **의존성 주입 (Dependency Injection)**: 시작 시점에 이 모든 것을 연결합니다.

### 제안 폴더 구조
```
src/
├── domain/                  # 1. 순수 비즈니스 로직 (의존성 없음)
│   ├── economic/
│   │   ├── models.py        # 데이터 모델 (Entity)
│   │   ├── services.py      # 도메인 서비스 (비즈니스 규칙/계산)
│   │   └── ports.py         # 인터페이스 정의 (Repository/Gateway)
│   └── analysis/
├── application/             # 2. 유스케이스 (오케스트레이션)
│   ├── economic/
│   │   └── collect_data_usecase.py
│   └── analysis/
│       └── generate_recommendation_usecase.py
├── adapters/                # 3. 인프라 구현 (외부 연동)
│   ├── input/
│   │   ├── kafka/           # Kafka Consumer (유스케이스 호출)
│   │   └── api/             # FastAPI Routes (유스케이스 호출)
│   └── output/
│       ├── persistence/     # MongoDB/Postgres Repositories
│       ├── external_api/    # FRED, Yahoo Finance Clients
│       └── notification/    # Slack Client
└── bootstrap/               # 4. 구성 및 연결
    └── container.py         # 의존성 주입 컨테이너
```

---

## 3. 기대 효과

| 특징 | 현재 (Current) | 제안 (Hexagonal) |
| :--- | :--- | :--- |
| **의존성 방향** | `Service -> Repository (구현체)` | `UseCase -> Port (인터페이스) <- Adapter` |
| **테스트 용이성** | 실제 DB/Kafka 필요 | **쉬운 단위 테스트** (Mock Port 사용 가능) |
| **유연성** | DB/API 변경 어려움 | **매우 쉬움** (새로운 Adapter 작성 후 주입만 변경) |
| **유지보수** | `main.py`가 "God Object"임 | **작고 집중된 파일들** |
| **프레임워크 독립성** | FastAPI/Confluent에 종속 | **독립적** (핵심 로직은 어디서든 실행 가능) |

## 4. 마이그레이션 계획
1.  **포트(Ports) 정의**: 현재 서비스에서 인터페이스를 추출합니다 (예: `IEconomicDataRepository`).
2.  **서비스 리팩토링**: 서비스가 의존성을 직접 생성하지 않고 `__init__`을 통해 주입받도록 변경합니다.
3.  **유스케이스(Use Cases) 추출**: `main.py`의 복잡한 로직을 애플리케이션 레이어의 유스케이스로 이동시킵니다.
4.  **어댑터(Adapters) 생성**: 현재의 구체적인 구현(Mongo, FRED 등)을 `adapters/` 폴더로 이동합니다.

## 5. 미래 확장성 및 런타임 대응 (Docker & Lambda)

해당 프로젝트가 향후 **분석 모듈 독립화** 또는 **AWS Lambda(FaaS)** 환경으로 전환될 가능성을 고려하여, 다음과 같은 설계 전략을 아키텍처에 반영합니다.

### 5.0 Docker 운영 전제
*   **상주 컨슈머 모델**: Kafka Consumer는 컨테이너 내에서 상주하며 메시지를 블로킹 대기로 처리합니다.
*   **입력 어댑터 분리**: Docker 환경에서는 `adapters/input/kafka`를 기본 엔트리로 사용하고, 런타임 변경 시 어댑터만 교체합니다.

### 5.1 왜 육각형 아키텍처인가?
*   **실행 환경 독립성**: 비즈니스 로직(UseCase)이 특정 프레임워크나 인터페이스(Kafka)에 종속되지 않으므로, 호출부(Adapter)만 Lambda Handler로 교체하면 즉시 클라우드 함수로 배포 가능합니다.
*   **인프라 교체 용이성**: 분석 결과 저장소가 로컬 DB에서 S3나 클라우드 네이티브 저장소로 변경되어도 도메인 로직은 영향을 받지 않습니다.

### 5.2 세부 설계 가이드
1.  **태스크 중심의 유스케이스 (Task-based UseCase)**: 거대한 서비스를 만들기보다 `AnalyzeStockUseCase`와 같이 하나의 분석 태스크를 수행하는 작은 함수/클래스 단위로 개발합니다. 이는 Lambda 함수 하나가 하나의 유스케이스를 담당하는 구조와 일치합니다.
2.  **도메인 순수성 유지 (Zero-Dependency Domain)**: `domain/` 폴더 내부에는 외부 라이브러리(Boto3, Kafka, API Clients) 임포트를 금지합니다. 오직 데이터 계산과 비즈니스 규칙에만 집중하여 가볍고 독립적인 모듈을 유지합니다.
3.  **경량화된 의존성 관리**: 각 유스케이스가 필요한 포트(Port)만 주입받도록 설계하여, 특정 모듈만 떼어서 배포할 때 불필요한 의존성이 포함되지 않도록 합니다.

---

## 6. 참고 자료 (References)

이 제안서는 다음의 아키텍처 패턴과 원칙을 기반으로 작성되었습니다.

1.  **Hexagonal Architecture (Ports and Adapters)**
    -   *Alistair Cockburn (2005)*. "Hexagonal Architecture".
    -   [Alistair Cockburn의 원문](https://alistair.cockburn.us/hexagonal-architecture/)
    -   애플리케이션을 도메인과 외부 세계(UI, DB 등)로 분리하여 테스트 용이성과 유지보수성을 높이는 아키텍처 패턴입니다.

2.  **Clean Architecture**
    -   *Robert C. Martin (Uncle Bob)*. "Clean Architecture: A Craftsman's Guide to Software Structure and Design".
    -   [The Clean Architecture Blog Post](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
    -   의존성 규칙(Dependency Rule)을 통해 안쪽 원(도메인)이 바깥쪽 원(인프라)에 의존하지 않도록 강제합니다.

3.  **Modular Monolith**
    -   *Kamil Grzybek*. "Modular Monolith: A Primer".
    -   마이크로서비스의 복잡성을 피하면서도 모듈성을 확보하는 전략으로, 현재 단계에서 완전한 MSA로 가기 전 중간 단계로 적합합니다.

4.  **Python Dependency Injection**
    -   *Martin Fowler*. "Inversion of Control Containers and the Dependency Injection pattern".
    -   Python에서는 `dependency-injector` 라이브러리나 팩토리 패턴을 사용하여 구현할 수 있습니다.
