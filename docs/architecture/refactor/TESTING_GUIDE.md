# Testing Guide

> **목적**: Backend 테스트 작성 가이드 (Kotest + MockK + Testcontainers)
> **대상**: Backend 개발자

---

## 📋 목차

1. [테스트 전략](#1-테스트-전략)
2. [단위 테스트 (Kotest + MockK)](#2-단위-테스트-kotest--mockk)
3. [통합 테스트 (Testcontainers)](#3-통합-테스트-testcontainers)
4. [테스트 픽스처](#4-테스트-픽스처)
5. [Property-Based Testing](#5-property-based-testing)

---

## 1. 테스트 전략

### 1.1 테스트 피라미드

```
        ┌─────────────┐
        │  E2E Tests  │  10% - API 전체 흐름
        ├─────────────┤
        │ Integration │  30% - 실제 DB, Kafka 사용
        │    Tests    │
        ├─────────────┤
        │    Unit     │  60% - 비즈니스 로직 격리
        │    Tests    │
        └─────────────┘
```

### 1.2 테스트 범위

| 계층 | 테스트 유형 | 도구 | 커버리지 목표 |
|------|-------------|------|---------------|
| **Domain** | 단위 테스트 | Kotest | 90%+ |
| **Application** | 단위 테스트 | Kotest + MockK | 80%+ |
| **Adapter (Input)** | 통합 테스트 | MockMvc + Testcontainers | 70%+ |
| **Adapter (Output)** | 통합 테스트 | Testcontainers | 70%+ |

### 1.3 의존성 설정

**build.gradle.kts**:
```kotlin
dependencies {
    // Kotest - Kotlin 테스트 프레임워크
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.kotest:kotest-property:5.8.0")

    // MockK - Kotlin 모킹 라이브러리
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("com.ninja-squad:springmockk:4.0.2")

    // Testcontainers - 실제 데이터베이스 테스트
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")
    testImplementation("org.testcontainers:kafka:1.19.3")
    testImplementation("org.testcontainers:mongodb:1.19.3")

    // ArchUnit - 아키텍처 검증
    testImplementation("com.tngtech.archunit:archunit-junit5:1.2.1")
}
```

---

## 2. 단위 테스트 (Kotest + MockK)

### 2.1 Domain 계층 테스트

**테스트 대상**: 순수 비즈니스 로직

```kotlin
package com.quantjumpstock.core.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Domain Model 단위 테스트
 *
 * ✅ 외부 의존성 없음
 * ✅ 비즈니스 규칙 검증
 * ✅ 불변성 검증
 */
class StrategyTest : StringSpec({

    "전략 이름은 3~100자여야 한다" {
        // Given & When & Then
        shouldThrow<IllegalArgumentException> {
            Strategy(
                id = null,
                name = "AB",  // 2자 - 실패
                type = StrategyType.MOMENTUM,
                status = StrategyStatus.DRAFT
            )
        }
    }

    "전략 이름은 필수다" {
        shouldThrow<IllegalArgumentException> {
            Strategy(
                id = null,
                name = "",
                type = StrategyType.MOMENTUM,
                status = StrategyStatus.DRAFT
            )
        }
    }

    "DRAFT 상태의 전략만 활성화할 수 있다" {
        // Given
        val draft = Strategy(
            id = 1L,
            name = "테스트 전략",
            type = StrategyType.MOMENTUM,
            status = StrategyStatus.DRAFT
        )

        // When
        val activated = draft.activate()

        // Then
        activated.status shouldBe StrategyStatus.ACTIVE
        activated.id shouldBe draft.id  // 불변성 확인
    }

    "ACTIVE 상태가 아닌 전략은 활성화할 수 없다" {
        // Given
        val paused = Strategy(
            id = 1L,
            name = "일시정지 전략",
            type = StrategyType.MOMENTUM,
            status = StrategyStatus.PAUSED
        )

        // When & Then
        shouldThrow<IllegalArgumentException> {
            paused.activate()
        }.message shouldBe "DRAFT 상태의 전략만 활성화할 수 있습니다 (현재: PAUSED)"
    }

    "전략 일시정지는 ACTIVE 상태에서만 가능하다" {
        // Given
        val active = Strategy(
            id = 1L,
            name = "활성 전략",
            type = StrategyType.VALUE,
            status = StrategyStatus.ACTIVE
        )

        // When
        val paused = active.pause()

        // Then
        paused.status shouldBe StrategyStatus.PAUSED
    }
})
```

### 2.2 Application 계층 테스트

**테스트 대상**: Use Case 로직, 오케스트레이션

```kotlin
package com.quantjumpstock.core.application.strategy

import com.quantjumpstock.core.domain.model.Strategy
import com.quantjumpstock.core.domain.model.StrategyStatus
import com.quantjumpstock.core.domain.model.StrategyType
import com.quantjumpstock.core.domain.port.output.StrategyRepository
import com.quantjumpstock.core.domain.event.DomainEventPublisher
import com.quantjumpstock.core.domain.event.StrategyCreatedEvent
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*

/**
 * Application Service 단위 테스트
 *
 * ✅ MockK로 도메인 포트 모킹
 * ✅ 비즈니스 로직 검증
 * ✅ 이벤트 발행 검증
 */
class StrategyServiceTest : StringSpec({

    lateinit var mockRepository: StrategyRepository
    lateinit var mockEventPublisher: DomainEventPublisher
    lateinit var service: StrategyService

    beforeTest {
        mockRepository = mockk()
        mockEventPublisher = mockk()
        service = StrategyService(mockRepository, mockEventPublisher)
    }

    "전략 생성 시 DRAFT 상태로 저장된다" {
        // Given
        val request = CreateStrategyRequest(
            name = "테스트 전략",
            type = StrategyType.MOMENTUM
        )

        every { mockRepository.save(any()) } returnsArgument 0
        every { mockEventPublisher.publish(any()) } just Runs

        // When
        val result = service.createStrategy(request)

        // Then
        result.name shouldBe "테스트 전략"
        result.type shouldBe StrategyType.MOMENTUM
        result.status shouldBe StrategyStatus.DRAFT

        verify(exactly = 1) { mockRepository.save(any()) }
    }

    "전략 생성 후 이벤트가 발행된다" {
        // Given
        val request = CreateStrategyRequest(
            name = "이벤트 테스트",
            type = StrategyType.VALUE
        )

        val savedStrategy = Strategy(
            id = 123L,
            name = "이벤트 테스트",
            type = StrategyType.VALUE,
            status = StrategyStatus.DRAFT
        )

        every { mockRepository.save(any()) } returns savedStrategy
        every { mockEventPublisher.publish(any()) } just Runs

        // When
        service.createStrategy(request)

        // Then
        verify {
            mockEventPublisher.publish(
                match<StrategyCreatedEvent> { it.strategyId == 123L }
            )
        }
    }

    "전략 활성화는 DRAFT 상태 전략만 가능하다" {
        // Given
        val draftStrategy = Strategy(
            id = 1L,
            name = "DRAFT 전략",
            type = StrategyType.MOMENTUM,
            status = StrategyStatus.DRAFT
        )

        every { mockRepository.findById(1L) } returns draftStrategy
        every { mockRepository.save(any()) } returnsArgument 0

        // When
        val result = service.activateStrategy(1L)

        // Then
        result.status shouldBe StrategyStatus.ACTIVE
        verify { mockRepository.save(match { it.status == StrategyStatus.ACTIVE }) }
    }

    "존재하지 않는 전략 활성화 시 예외 발생" {
        // Given
        every { mockRepository.findById(999L) } returns null

        // When & Then
        val exception = shouldThrow<StrategyNotFoundException> {
            service.activateStrategy(999L)
        }

        exception.message shouldBe "전략을 찾을 수 없습니다: 999"
    }

    "활성 전략 목록을 조회할 수 있다" {
        // Given
        val activeStrategies = listOf(
            Strategy(1L, "전략1", StrategyType.MOMENTUM, StrategyStatus.ACTIVE),
            Strategy(2L, "전략2", StrategyType.VALUE, StrategyStatus.ACTIVE)
        )

        every { mockRepository.findAllActive() } returns activeStrategies

        // When
        val result = service.findActiveStrategies()

        // Then
        result.size shouldBe 2
        result.all { it.status == StrategyStatus.ACTIVE } shouldBe true
    }
})
```

### 2.3 MockK 주요 기능

```kotlin
// 1. 기본 모킹
val mock = mockk<StrategyRepository>()

// 2. 반환값 지정
every { mock.findById(1L) } returns Strategy(...)

// 3. 인자 반환 (save 등에 유용)
every { mock.save(any()) } returnsArgument 0

// 4. void 함수 모킹
every { mock.delete(any()) } just Runs

// 5. 예외 발생
every { mock.findById(999L) } throws StrategyNotFoundException()

// 6. 호출 검증
verify { mock.save(any()) }
verify(exactly = 1) { mock.findById(1L) }
verify(exactly = 0) { mock.delete(any()) }

// 7. 인자 매칭
verify { mock.save(match { it.name == "테스트" }) }

// 8. 순서 검증
verifyOrder {
    mock.findById(1L)
    mock.save(any())
}
```

---

## 3. 통합 테스트 (Testcontainers)

### 3.1 Persistence Adapter 통합 테스트

```kotlin
package com.quantjumpstock.core.adapter.output.persistence.jpa.adapter

import com.quantjumpstock.core.domain.model.Strategy
import com.quantjumpstock.core.domain.model.StrategyStatus
import com.quantjumpstock.core.domain.model.StrategyType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Persistence Adapter 통합 테스트
 *
 * ✅ Testcontainers로 실제 PostgreSQL 사용
 * ✅ JPA 쿼리 검증
 * ✅ 매핑 로직 검증
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(StrategyPersistenceAdapter::class, StrategyMapper::class)
class StrategyPersistenceAdapterTest(
    private val adapter: StrategyPersistenceAdapter
) : StringSpec({

    "전략을 저장하고 ID가 자동 생성된다" {
        // Given
        val strategy = Strategy(
            id = null,
            name = "저장 테스트",
            type = StrategyType.MOMENTUM,
            status = StrategyStatus.DRAFT
        )

        // When
        val saved = adapter.save(strategy)

        // Then
        saved.id shouldNotBe null
        saved.name shouldBe "저장 테스트"
    }

    "저장된 전략을 ID로 조회할 수 있다" {
        // Given
        val saved = adapter.save(
            Strategy(null, "조회 테스트", StrategyType.VALUE, StrategyStatus.ACTIVE)
        )

        // When
        val found = adapter.findById(saved.id!!)

        // Then
        found shouldNotBe null
        found!!.id shouldBe saved.id
        found.name shouldBe "조회 테스트"
    }

    "존재하지 않는 ID 조회 시 null 반환" {
        // When
        val result = adapter.findById(999999L)

        // Then
        result shouldBe null
    }

    "상태별로 전략을 필터링할 수 있다" {
        // Given
        adapter.save(Strategy(null, "전략1", StrategyType.MOMENTUM, StrategyStatus.ACTIVE))
        adapter.save(Strategy(null, "전략2", StrategyType.VALUE, StrategyStatus.ACTIVE))
        adapter.save(Strategy(null, "전략3", StrategyType.GROWTH, StrategyStatus.DRAFT))

        // When
        val activeStrategies = adapter.findAllByStatus(StrategyStatus.ACTIVE)

        // Then
        activeStrategies.size shouldBe 2
        activeStrategies.all { it.status == StrategyStatus.ACTIVE } shouldBe true
    }

    "전략을 삭제할 수 있다" {
        // Given
        val saved = adapter.save(
            Strategy(null, "삭제 테스트", StrategyType.MOMENTUM, StrategyStatus.DRAFT)
        )

        // When
        adapter.delete(saved.id!!)

        // Then
        val found = adapter.findById(saved.id!!)
        found shouldBe null
    }

}) {
    companion object {
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
            withDatabaseName("test")
            withUsername("test")
            withPassword("test")
        }
    }
}
```

### 3.2 REST Controller 통합 테스트

```kotlin
package com.quantjumpstock.core.adapter.input.rest.strategy

import com.ninjasquad.springmockk.MockkBean
import com.quantjumpstock.core.application.strategy.StrategyService
import com.quantjumpstock.core.domain.model.Strategy
import com.quantjumpstock.core.domain.model.StrategyStatus
import com.quantjumpstock.core.domain.model.StrategyType
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.*

/**
 * REST Controller 통합 테스트
 *
 * ✅ MockMvc로 HTTP 요청/응답 검증
 * ✅ Serialization/Deserialization 검증
 * ✅ Spring Security 통합 테스트
 */
@WebMvcTest(StrategyController::class)
class StrategyControllerTest(
    private val mockMvc: MockMvc,

    @MockkBean
    private val mockService: StrategyService
) : StringSpec({

    "POST /api/strategies - 전략 생성" {
        // Given
        val request = """
            {
                "name": "테스트 전략",
                "type": "MOMENTUM"
            }
        """.trimIndent()

        val createdStrategy = Strategy(
            id = 1L,
            name = "테스트 전략",
            type = StrategyType.MOMENTUM,
            status = StrategyStatus.DRAFT
        )

        every { mockService.createStrategy(any()) } returns createdStrategy

        // When & Then
        mockMvc.post("/api/strategies") {
            contentType = MediaType.APPLICATION_JSON
            content = request
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { value(1) }
            jsonPath("$.name") { value("테스트 전략") }
            jsonPath("$.status") { value("DRAFT") }
        }
    }

    "GET /api/strategies/active - 활성 전략 목록 조회" {
        // Given
        val strategies = listOf(
            Strategy(1L, "전략1", StrategyType.MOMENTUM, StrategyStatus.ACTIVE),
            Strategy(2L, "전략2", StrategyType.VALUE, StrategyStatus.ACTIVE)
        )

        every { mockService.findActiveStrategies() } returns strategies

        // When & Then
        mockMvc.get("/api/strategies/active")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].status") { value("ACTIVE") }
                jsonPath("$[1].status") { value("ACTIVE") }
            }
    }

    "PATCH /api/strategies/{id}/activate - 전략 활성화" {
        // Given
        val activated = Strategy(
            id = 1L,
            name = "활성화 전략",
            type = StrategyType.MOMENTUM,
            status = StrategyStatus.ACTIVE
        )

        every { mockService.activateStrategy(1L) } returns activated

        // When & Then
        mockMvc.patch("/api/strategies/1/activate")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("ACTIVE") }
            }
    }
})
```

### 3.3 MongoDB Adapter 통합 테스트

```kotlin
package com.quantjumpstock.core.adapter.output.persistence.mongodb.adapter

import com.quantjumpstock.core.domain.model.Stock
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate

@DataMongoTest
@Testcontainers
@ActiveProfiles("test")
@Import(StockPersistenceAdapter::class, StockMapper::class)
class StockPersistenceAdapterTest(
    private val adapter: StockPersistenceAdapter
) : StringSpec({

    "주식 데이터를 저장하고 조회할 수 있다" {
        // Given
        val stock = Stock(
            id = null,
            symbol = "005930",
            stockName = "삼성전자",
            closePrice = 75000.0,
            date = LocalDate.of(2024, 1, 15)
        )

        // When
        val saved = adapter.save(stock)

        // Then
        saved.id shouldNotBe null
        saved.symbol shouldBe "005930"
        saved.closePrice shouldBe 75000.0
    }

    "종목 코드와 날짜로 주식 데이터를 조회할 수 있다" {
        // Given
        val date = LocalDate.of(2024, 1, 20)
        adapter.save(Stock(null, "005930", "삼성전자", 76000.0, date))

        // When
        val found = adapter.findBySymbolAndDate("005930", date)

        // Then
        found shouldNotBe null
        found!!.closePrice shouldBe 76000.0
    }

    "날짜 범위로 주식 데이터를 조회할 수 있다" {
        // Given
        adapter.save(Stock(null, "005930", "삼성전자", 75000.0, LocalDate.of(2024, 1, 10)))
        adapter.save(Stock(null, "005930", "삼성전자", 76000.0, LocalDate.of(2024, 1, 15)))
        adapter.save(Stock(null, "005930", "삼성전자", 77000.0, LocalDate.of(2024, 1, 20)))

        // When
        val stocks = adapter.findBySymbolBetweenDates(
            symbol = "005930",
            startDate = LocalDate.of(2024, 1, 12),
            endDate = LocalDate.of(2024, 1, 18)
        )

        // Then
        stocks.size shouldBe 1
        stocks[0].closePrice shouldBe 76000.0
    }

}) {
    companion object {
        @Container
        val mongo = MongoDBContainer("mongo:7.0").apply {
            withExposedPorts(27017)
        }
    }
}
```

---

## 4. 테스트 픽스처

### 4.1 Builder Pattern

**위치**: `src/test/kotlin/com/quantjumpstock/core/fixtures/`

```kotlin
package com.quantjumpstock.core.fixtures

import com.quantjumpstock.core.domain.model.Strategy
import com.quantjumpstock.core.domain.model.StrategyStatus
import com.quantjumpstock.core.domain.model.StrategyType

/**
 * Strategy 테스트 픽스처 빌더
 *
 * ✅ 테스트 데이터 생성 간소화
 * ✅ 가독성 향상
 * ✅ 유지보수 용이
 */
class StrategyFixture(
    private var id: Long? = null,
    private var name: String = "테스트 전략",
    private var type: StrategyType = StrategyType.MOMENTUM,
    private var status: StrategyStatus = StrategyStatus.DRAFT
) {

    fun withId(id: Long) = apply { this.id = id }
    fun withName(name: String) = apply { this.name = name }
    fun withType(type: StrategyType) = apply { this.type = type }
    fun withStatus(status: StrategyStatus) = apply { this.status = status }

    fun build() = Strategy(
        id = id,
        name = name,
        type = type,
        status = status
    )

    companion object {
        fun aStrategy() = StrategyFixture()

        fun aDraftStrategy() = StrategyFixture()
            .withStatus(StrategyStatus.DRAFT)

        fun anActiveStrategy() = StrategyFixture()
            .withId(1L)
            .withStatus(StrategyStatus.ACTIVE)
    }
}

// 사용 예시
val strategy = StrategyFixture.aStrategy()
    .withName("모멘텀 전략")
    .withType(StrategyType.MOMENTUM)
    .build()

val activeStrategy = StrategyFixture.anActiveStrategy().build()
```

---

## 5. Property-Based Testing

### 5.1 금융 계산 검증

```kotlin
package com.quantjumpstock.core.domain.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.filter
import io.kotest.property.checkAll

/**
 * Property-Based Testing
 *
 * ✅ 다양한 입력값으로 자동 테스트
 * ✅ 금융 계산 로직 검증에 유용
 */
class TechnicalIndicatorCalculatorTest : StringSpec({

    val calculator = TechnicalIndicatorCalculator()

    "이동평균은 항상 최소값보다 크거나 같고 최대값보다 작거나 같다" {
        checkAll(
            Arb.double(min = 1.0, max = 1000.0).filter { it > 0 }
        ) { price ->
            val prices = List(20) { price * (0.9 + Math.random() * 0.2) }
            val sma = calculator.calculateSMA(prices, 20)

            sma shouldBeGreaterThan prices.minOrNull()!!
            sma shouldBeLessThan prices.maxOrNull()!!
        }
    }

    "Sharpe Ratio는 무위험 수익률이 증가하면 감소한다" {
        checkAll(1000) { iteration ->
            val returns = List(100) { Math.random() * 0.2 - 0.1 }  // -10% ~ +10%
            val riskFreeRate1 = 0.02
            val riskFreeRate2 = 0.05

            val sharpe1 = calculator.calculateSharpeRatio(returns, riskFreeRate1)
            val sharpe2 = calculator.calculateSharpeRatio(returns, riskFreeRate2)

            if (sharpe1 > 0) {
                sharpe2 shouldBeLessThan sharpe1
            }
        }
    }
})
```

---

## 6. 실행 및 커버리지

### 6.1 테스트 실행

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests "StrategyServiceTest"

# 특정 패키지 테스트 실행
./gradlew test --tests "com.quantjumpstock.core.domain.*"

# 통합 테스트만 실행
./gradlew test --tests "*IntegrationTest"

# 병렬 실행 (속도 향상)
./gradlew test --parallel --max-workers=4
```

### 6.2 커버리지 리포트

```bash
# Jacoco 커버리지 생성
./gradlew test jacocoTestReport

# 리포트 위치
open build/reports/jacoco/test/html/index.html
```

### 6.3 목표 커버리지

```kotlin
// build.gradle.kts
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()  // 80% 이상
            }
        }
    }
}
```

---

## 7. 체크리스트

### 단위 테스트 작성 시

- [ ] 테스트가 독립적으로 실행되는가?
- [ ] 외부 의존성을 모킹했는가?
- [ ] 경계 케이스를 테스트했는가?
- [ ] 테스트 이름이 명확한가?
- [ ] Given-When-Then 구조를 따르는가?

### 통합 테스트 작성 시

- [ ] Testcontainers를 사용하는가?
- [ ] 테스트 데이터를 정리(cleanup)하는가?
- [ ] 트랜잭션 롤백을 사용하는가?
- [ ] 실제 프로덕션 환경과 유사한가?

### 성능 고려사항

- [ ] 통합 테스트가 너무 느리지 않은가? (< 5초)
- [ ] Testcontainers를 재사용하는가?
- [ ] 불필요한 데이터베이스 조회가 없는가?
- [ ] 병렬 실행이 가능한가?
