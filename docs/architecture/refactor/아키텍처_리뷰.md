# 아키텍처 분석 및 제안 보고서

## 1. 배경
`quant-jump-stock-core` 프로젝트는 **헥사고날 아키텍처(Hexagonal Architecture)**(포트와 어댑터 패턴)를 도입하여 낮은 결합도와 높은 유연성을 확보하는 것을 목표로 하고 있습니다. 그러나 현재 구현 상태는 이러한 아키텍처 패턴에서 크게 벗어나 있어, 의도했던 이점을 얻지 못한 채 구조적 복잡성만 높아진 상태입니다.

## 2. 현재 아키텍처 분석: "무늬만 헥사고날"
현재 구조는 겉모습(패키지명: `adapter`, `port`, `domain`)은 헥사고날 아키텍처를 따르고 있지만, 실제 동작 방식은 강하게 결합된 레이어드 아키텍처(Layered Architecture)입니다.

### 2.1. 핵심 문제점
| 영역 | 문제점 | 심각도 |
| :--- | :--- | :--- |
| **도메인 순수성** | **치명적**. 도메인 객체(`Stock`, `Strategy`)에 `@Entity`, `@Document`, `@Id` 등의 어노테이션이 붙어 있습니다. 이는 비즈니스 모델이 아니라 사실상 DB 스키마입니다. | 🔴 매우 높음 |
| **의존성 방향** | **역전됨**. 도메인 인터페이스(`StrategyRepository`)가 어댑터 클래스(`StrategyEntity`)를 import 하고 있습니다. "내부"가 "외부"에 의존하는 형태입니다. | 🔴 매우 높음 |
| **애플리케이션 계층** | **우회됨**. 서비스(`StrategyService`, `AutoTradingService`)가 JPA/Mongo Repository를 직접 주입받아 사용합니다. 도메인 로직을 조율하는 대신 단순한 "트랜잭션 스크립트" 역할을 하고 있습니다. | 🟠 높음 |
| **포트(Ports)** | **누락/오용**. Output Port는 존재하나 구현 세부 사항(Entity)을 노출하고 있습니다. Input Port(UseCases)는 거의 없으며, 컨트롤러가 서비스를 직접 호출합니다. | 🟠 높음 |

### 2.2. 왜 "애매하다"고 느껴지는가?
사용자께서 느끼신 "애매함"은 **의도와 현실의 괴리**에서 옵니다.
- **의도**: "도메인 로직을 데이터베이스와 분리하고 싶다."
- **현실**: "내 도메인 로직이 곧 데이터베이스 스키마이다."

복잡한 폴더 구조(Input/Output, Ports/Adapters)를 유지하는 비용은 치르고 있지만, 그에 따른 이득(테스트 용이성, 부품 교체 가능성)은 전혀 얻지 못하고 있는 상태입니다.

---

## 3. 추천 아키텍처: "실용적 클린 아키텍처"

가장 효율적이고 유연하며, 진정한 **"의존성 없는(Dependency-Free)"** 구조를 만들기 위해 **엄격한 도메인 중심** 접근 방식을 제안합니다. 이는 흔히 **클린 아키텍처(Clean Architecture)** 또는 **순수 헥사고날 아키텍처**라고 불립니다.

### 3.1. 대원칙 (The Golden Rule)
> **`domain` 패키지는 `adapter`, `infrastructure`, `application` 패키지의 그 어떤 것도 import 해서는 안 됩니다.**
> 오직 자바/코틀린 표준 라이브러리만 사용해야 합니다.

### 3.2. 제안하는 구조도

```mermaid
graph TD
    subgraph "Infrastructure / Frameworks (외부)"
        Web[웹 컨트롤러]
        DB[JPA / MongoDB]
        Ext[외부 API]
    end

    subgraph "Interface Adapters (변환기)"
        CtlAdapter[컨트롤러 매퍼]
        RepoAdapter[영속성 어댑터]
    end

    subgraph "Application Core (애플리케이션)"
        Service[서비스 / 유스케이스]
        PortIn[Input Port (인터페이스)]
        PortOut[Output Port (인터페이스)]
    end

    subgraph "Domain (핵심 코어)"
        Model[순수 도메인 모델]
        Logic[도메인 로직]
    end

    Web --> CtlAdapter
    CtlAdapter --> PortIn
    Service -.-> PortIn
    Service --> PortOut
    Service --> Model
    RepoAdapter -.-> PortOut
    RepoAdapter --> DB
    RepoAdapter --> Model
```

### 3.3. 필수 변경 사항

#### A. "도메인 모델"과 "Entity"의 분리
하나의 개념에 대해 **두 개의 클래스**가 필요합니다.
1.  **도메인 모델 (`com.quantjumpstock.core.domain.model.Strategy`)**:
    -   순수 Kotlin 데이터 클래스 (POJO/POKO).
    -   어노테이션 없음 (`@Entity`, `@GET` 금지).
    -   비즈니스 로직 포함 (예: `fun calculateProfit()`).
2.  **JPA Entity (`com.quantjumpstock.core.adapter.out.persistence.entity.StrategyEntity`)**:
    -   DB 테이블 정의.
    -   `@Entity`, `@Column`, `@JoinColumn` 등으로 가득 참.
    -   단순 데이터 컨테이너.

#### B. 매퍼(Mapper) 도입
이 둘 사이를 변환해 주는 번역기가 필요합니다.
-   `RepoAdapter.save(domainModel)` -> 도메인 모델을 Entity로 변환 -> `jpaRepo.save(entity)` -> 결과를 다시 도메인 모델로 변환 -> 반환.

#### C. 유스케이스(UseCase) 정의
단순히 뭉뚱그려진 `StrategyService` 대신, 명확한 행위를 정의합니다.
-   `interface CreateStrategyUseCase`
-   `interface BacktestStrategyUseCase`
이것이 **Input Port**가 됩니다. 컨트롤러는 구체적인 서비스 클래스가 아니라 이 인터페이스를 호출해야 합니다.

### 3.4. 평가

| 특징 | 현재 구조 | 제안 구조 |
| :--- | :--- | :--- |
| **DB 교체** | **불가능**. 비즈니스 로직도 다시 짜야 함. | **쉬움**. 어댑터만 갈아 끼우면 됨. |
| **테스트** | 느림 (스프링 컨텍스트 필요) | **빠름**. 순수 단위 테스트 가능. |
| **코드량** | 적음 (클래스 하나로 퉁침) | **보통** (분리를 위한 중복 코드 발생) |
| **복잡도** | 혼란스러움 (섞여 있음) | **명확함** (분리되어 있음) |

## 4. 결론
소프트웨어 아키텍처에서 "효율성"이란 당장 코드를 적게 짜는 것이 아니라, **내일 코드를 얼마나 빨리 이해하고 안전하게 바꿀 수 있느냐**입니다.

현재 구조는 **기술 부채(Technical Debt)**입니다. "의존성 없는 시스템"이라는 목표를 달성하기 위해 **제안된 구조(제대로 된 헥사고날)**로 리팩토링하는 것을 강력히 추천합니다.
