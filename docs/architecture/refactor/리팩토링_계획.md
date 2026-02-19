# 아키텍처 리팩토링 구현 계획 (Implementation Plan)

## 1. 목표 (Goal)
`quant-jump-stock-core` 프로젝트를 **엄격한 헥사고날 아키텍처(Clean Architecture)**로 리팩토링하여, 도메인 로직과 외부 기술(JPA, MongoDB 등) 간의 결합을 완전히 제거합니다.

> **핵심 원칙**: `domain` 패키지는 외부 패키지(`adapter`, `infrastructure`)를 절대 참조하지 않는다.

## 2. 변경 범위 (Scope)
먼저 **`Strategy` (전략) 도메인**을 파일럿으로 선정하여 리팩토링을 진행합니다. 이 패턴이 검증되면 다른 도메인(`User`, `Trade` 등)으로 확산합니다.

## 3. 상세 구현 단계

### Phase 1: 기반 구조 및 도메인 격리 (Foundation & Domain Isolation)
- [ ] **순수 도메인 모델 생성**
    - `com.quantjumpstock.core.domain.model.Strategy` 생성 (Kotlin Data Class).
    - JPA 어노테이션(`@Entity` 등)이 **없는** 순수 객체로 정의.
    - 기존 `Stock` 등의 의존성도 순수 모델로 변경.
- [ ] **포트(Port) 인터페이스 재정의**
    - `StrategyRepository` (Output Port)가 `StrategyEntity` 대신 `model.Strategy`를 사용하도록 수정.
    - `CreateStrategyUseCase` (Input Port) 인터페이스 신규 생성.

### Phase 2: 어댑터 계층 재구성 (Adapter Refactoring)
- [ ] **JPA Entity 격리**
    - 기존 `StrategyEntity`를 `adapter.out.persistence.entity` 패키지로 이동 (또는 명확히 분리).
    - 도메인 모델과는 별개로 DB 매핑 전용 객체로 관리.
- [ ] **Mapper(변환기) 구현**
    - `StrategyMapper` 구현: `Domain Model` ↔ `JPA Entity` 변환 로직 작성.
- [ ] **Persistence Adapter 구현**
    - `StrategyPersistenceAdapter`가 `StrategyRepository` 인터페이스를 구현.
    - 내부적으로 `StrategyJpaRepository`를 사용하여 DB 접근 후, Mapper를 통해 변환하여 반환.

### Phase 3: 애플리케이션 계층 수정 (Application Layer)
- [ ] **Service 의존성 정리**
    - `StrategyService`가 JPA Repository가 아닌 `StrategyRepository` (Port)만 의존하도록 수정.
    - 비즈니스 로직에서 Entity 관련 코드 제거.
    - `CreateStrategyUseCase` 등 Input Port 인터페이스 구현.

### Phase 4: 웹 어댑터 수정 (Web Adapter)
- [ ] **Controller 수정**
    - `StrategyController`가 Service 구현체가 아닌 `UseCase` 인터페이스를 의존하도록 변경.
    - DTO ↔ 도메인 모델 변환 로직 확인.

## 4. 검증 계획 (Verification Plan)
### 자동화 테스트
- [ ] **도메인 단위 테스트**: DB 없이 `Strategy` 도메인 로직만 테스트 (가장 중요).
- [ ] **아키텍처 테스트**: ArchUnit 등을 활용하여 `domain` 패키지가 외부를 참조하지 않는지 검증.

### 수동 검증
- [ ] 애플리케이션 실행 후 전략 생성/조회 API가 정상 동작하는지 확인 (Postman/Swagger).
- [ ] DB에 데이터가 정상적으로 저장되는지 확인.
