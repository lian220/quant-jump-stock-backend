# Data Engine 리팩토링 계획서

## 목적
- 결합도를 낮춰 테스트 가능성과 변경 용이성을 높인다.
- 확장 시 `main.py` 수정 비용을 최소화한다.
- 외부 의존성을 어댑터로 분리해 교체 비용을 낮춘다.

## 현재 구조 문제 요약
- `main.py`에 Kafka 소비, 라우팅, 비즈니스 로직 호출이 집중되어 비대함.
- 서비스가 DB/외부 API 구현체를 직접 생성해 결합도가 높음.
- 레이어 경계가 모호하고 기능 추가 시 수정 범위가 넓음.
- 전역 싱글톤 의존으로 테스트가 어려움.

## 목표 아키텍처(권장)
Hexagonal Architecture(Ports/Adapters) 기반.

### 권장 디렉터리 구조
```
src/
├── domain/
│   ├── economic/
│   │   ├── models.py
│   │   ├── ports.py
│   │   └── services.py
│   ├── analysis/
│   │   ├── models.py
│   │   ├── ports.py
│   │   └── services.py
│   └── shared/
├── application/
│   ├── economic/
│   │   └── collect_data_usecase.py
│   ├── analysis/
│   │   ├── technical_analysis_usecase.py
│   │   ├── sentiment_analysis_usecase.py
│   │   └── combined_analysis_usecase.py
│   └── shared/
│       └── notification_service.py
├── adapters/
│   ├── input/
│   │   ├── kafka/
│   │   │   ├── consumer.py
│   │   │   ├── router.py
│   │   │   └── handlers/
│   │   └── api/
│   │       ├── app.py
│   │       └── routers/
│   └── output/
│       ├── persistence/
│       ├── external_api/
│       ├── messaging/
│       └── notification/
├── bootstrap/
│   ├── container.py
│   └── wire.py
└── main.py
```

## 리팩토링 핵심 원칙
- 도메인은 외부 의존성을 몰라야 한다.
- 유스케이스는 도메인 + 포트 인터페이스만 의존한다.
- 인프라(DB, Kafka, 외부 API)는 어댑터로 분리한다.
- `main.py`는 부트스트랩만 담당한다.

## 단계별 마이그레이션 계획

### 1단계: 포트(인터페이스) 정의
- `domain/*/ports.py`에 Repository/Client 인터페이스 정의.
- 기존 구현체는 변경하지 않고 인터페이스만 추가.

### 2단계: 어댑터 분리
- 기존 DB/외부 API 접근 코드를 `adapters/output`으로 이동.
- 포트 인터페이스 구현체로 감싼다.

### 3단계: 유스케이스 추출
- `services/`의 오케스트레이션 로직을 `application/`로 이동.
- `main.py`에서 비즈니스 로직 호출을 제거.

### 4단계: Kafka 핸들러 분리
- 토픽별 핸들러를 `adapters/input/kafka/handlers`로 분리.
- `router.py`에서 토픽 → 핸들러 매핑.

### 5단계: DI 컨테이너 도입
- 의존성 주입 컨테이너(`dependency-injector` 권장) 적용.
- `bootstrap/container.py`에서 의존성 연결.

### 6단계: main.py 최소화
- API 서버 실행 + Kafka 소비 시작만 담당.
- 50줄 내외 부트스트랩 유지.

## 기대 효과
- 테스트 용이성 향상(모의 객체 주입 가능)
- DB/외부 API 교체 용이성 확보
- 토픽 추가 시 수정 범위 축소
- 코드 이해도 및 유지보수성 개선

## 후속 작업 후보
- 슬랙 알림/외부 API 클라이언트를 공통 어댑터로 통합
- 공용 에러 처리/로깅 전략 정리
- 이벤트 스키마 버저닝 전략 추가
