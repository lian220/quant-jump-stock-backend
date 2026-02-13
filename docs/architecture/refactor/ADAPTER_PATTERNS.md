# Adapter Patterns Guide

> **목적**: Hexagonal Architecture의 Adapter 패턴 구현 가이드
> **대상**: Backend 개발자, 새 서비스 작성 시 참고

---

## 📋 목차

1. [Persistence Adapter 패턴](#1-persistence-adapter-패턴)
2. [매핑 전략](#2-매핑-전략)
3. [테스트 패턴](#3-테스트-패턴)
4. [실전 예제](#4-실전-예제)

---

## 1. Persistence Adapter 패턴

### 1.1 기본 구조

```
adapter/output/persistence/jpa/
├── entity/                    # JPA 엔티티
│   └── StrategyEntity.kt
├── repository/                # Spring Data JPA 리포지토리
│   └── StrategyJpaRepository.kt
├── adapter/                   # 도메인 포트 구현체
│   └── StrategyPersistenceAdapter.kt
└── mapper/                    # 매핑 로직
    └── StrategyMapper.kt
```

### 1.2 JPA Entity (Infrastructure)

**위치**: `adapter/output/persistence/jpa/entity/`

```kotlin
package com.quantjumpstock.core.adapter.output.persistence.jpa.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * JPA Entity - 데이터베이스 테이블 매핑
 *
 * ⚠️ 주의:
 * - 이 클래스는 infrastructure 계층에만 존재
 * - application이나 domain에서 절대 import 금지
 */
@Entity
@Table(name = "strategies")
class StrategyEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy_type", nullable = false)
    var strategyType: StrategyType,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: StrategyStatus,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    @PreUpdate
    fun preUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
```

### 1.3 Spring Data JPA Repository

**위치**: `adapter/output/persistence/jpa/repository/`

```kotlin
package com.quantjumpstock.core.adapter.output.persistence.jpa.repository

import com.quantjumpstock.core.adapter.output.persistence.jpa.entity.StrategyEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * Spring Data JPA Repository
 *
 * ✅ 이곳에서 JPA 쿼리 메서드 정의
 * ✅ @Query로 복잡한 쿼리 작성 가능
 */
@Repository
interface StrategyJpaRepository : JpaRepository<StrategyEntity, Long> {

    fun findByStatus(status: StrategyStatus): List<StrategyEntity>

    @Query("SELECT s FROM StrategyEntity s WHERE s.status = 'ACTIVE' ORDER BY s.createdAt DESC")
    fun findAllActiveOrderByCreatedAtDesc(): List<StrategyEntity>
}
```

### 1.4 Domain Model (Pure Kotlin)

**위치**: `domain/model/`

```kotlin
package com.quantjumpstock.core.domain.model

/**
 * 순수 도메인 모델 - 비즈니스 로직 포함
 *
 * ✅ 어떤 프레임워크 어노테이션도 없음
 * ✅ 불변성 (val 사용)
 * ✅ 비즈니스 규칙 검증 (init, 메서드)
 */
data class Strategy(
    val id: Long?,
    val name: String,
    val type: StrategyType,
    val status: StrategyStatus,
    val createdAt: java.time.LocalDateTime = java.time.LocalDateTime.now()
) {
    init {
        require(name.length in 3..100) { "전략 이름은 3~100자여야 합니다" }
        require(name.isNotBlank()) { "전략 이름은 필수입니다" }
    }

    /**
     * 비즈니스 로직: 전략 활성화
     */
    fun activate(): Strategy {
        require(status == StrategyStatus.DRAFT) {
            "DRAFT 상태의 전략만 활성화할 수 있습니다 (현재: $status)"
        }
        return copy(status = StrategyStatus.ACTIVE)
    }

    /**
     * 비즈니스 로직: 전략 일시정지
     */
    fun pause(): Strategy {
        require(status == StrategyStatus.ACTIVE) {
            "ACTIVE 상태의 전략만 일시정지할 수 있습니다"
        }
        return copy(status = StrategyStatus.PAUSED)
    }
}
```

### 1.5 Domain Port (Interface)

**위치**: `domain/port/output/`

```kotlin
package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.Strategy
import com.quantjumpstock.core.domain.model.StrategyStatus

/**
 * 도메인 포트 - Repository 인터페이스
 *
 * ✅ 도메인 타입만 사용 (Strategy)
 * ❌ Infrastructure 타입 사용 금지 (StrategyEntity)
 */
interface StrategyRepository {
    fun save(strategy: Strategy): Strategy
    fun findById(id: Long): Strategy?
    fun findAllByStatus(status: StrategyStatus): List<Strategy>
    fun findAllActive(): List<Strategy>
    fun delete(id: Long)
    fun existsById(id: Long): Boolean
}
```

### 1.6 Persistence Adapter (구현체)

**위치**: `adapter/output/persistence/jpa/adapter/`

```kotlin
package com.quantjumpstock.core.adapter.output.persistence.jpa.adapter

import com.quantjumpstock.core.adapter.output.persistence.jpa.repository.StrategyJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.mapper.StrategyMapper
import com.quantjumpstock.core.domain.model.Strategy
import com.quantjumpstock.core.domain.model.StrategyStatus
import com.quantjumpstock.core.domain.port.output.StrategyRepository
import org.springframework.stereotype.Repository

/**
 * Persistence Adapter - 도메인 포트 구현
 *
 * ✅ JPA Repository를 사용하지만 도메인 타입으로 변환
 * ✅ 매핑 로직을 Mapper에 위임
 */
@Repository
class StrategyPersistenceAdapter(
    private val jpaRepository: StrategyJpaRepository,
    private val mapper: StrategyMapper
) : StrategyRepository {

    override fun save(strategy: Strategy): Strategy {
        val entity = mapper.toEntity(strategy)
        val saved = jpaRepository.save(entity)
        return mapper.toDomain(saved)
    }

    override fun findById(id: Long): Strategy? {
        return jpaRepository.findById(id)
            .map { mapper.toDomain(it) }
            .orElse(null)
    }

    override fun findAllByStatus(status: StrategyStatus): List<Strategy> {
        return jpaRepository.findByStatus(status)
            .map { mapper.toDomain(it) }
    }

    override fun findAllActive(): List<Strategy> {
        return jpaRepository.findAllActiveOrderByCreatedAtDesc()
            .map { mapper.toDomain(it) }
    }

    override fun delete(id: Long) {
        jpaRepository.deleteById(id)
    }

    override fun existsById(id: Long): Boolean {
        return jpaRepository.existsById(id)
    }
}
```

---

## 2. 매핑 전략

### 2.1 Mapper Component 방식 (추천)

**위치**: `adapter/output/persistence/jpa/mapper/`

**장점**:
- 복잡한 매핑 로직 캡슐화
- 다른 Repository 참조 가능 (연관관계 매핑)
- 테스트 가능

```kotlin
package com.quantjumpstock.core.adapter.output.persistence.jpa.mapper

import com.quantjumpstock.core.adapter.output.persistence.jpa.entity.StrategyEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.repository.CategoryJpaRepository
import com.quantjumpstock.core.domain.model.Strategy
import org.springframework.stereotype.Component

/**
 * Mapper Component
 *
 * ✅ 양방향 매핑: toEntity, toDomain
 * ✅ 다른 Repository 주입 가능 (연관관계 처리)
 * ✅ 복잡한 변환 로직 캡슐화
 */
@Component
class StrategyMapper(
    private val categoryRepository: CategoryJpaRepository
) {

    fun toEntity(domain: Strategy): StrategyEntity {
        return StrategyEntity(
            id = domain.id,
            name = domain.name,
            strategyType = domain.type,
            status = domain.status,
            createdAt = domain.createdAt
        )
    }

    fun toDomain(entity: StrategyEntity): Strategy {
        return Strategy(
            id = entity.id,
            name = entity.name,
            type = entity.strategyType,
            status = entity.status,
            createdAt = entity.createdAt
        )
    }

    /**
     * 리스트 변환 헬퍼 메서드
     */
    fun toDomainList(entities: List<StrategyEntity>): List<Strategy> {
        return entities.map { toDomain(it) }
    }
}
```

### 2.2 Extension Function 방식

**위치**: `adapter/output/persistence/jpa/adapter/` (Adapter 파일 내)

**장점**:
- 간단한 매핑에 적합
- 보일러플레이트 코드 최소화

**단점**:
- 다른 Repository 참조 불가
- 복잡한 매핑에 부적합

```kotlin
package com.quantjumpstock.core.adapter.output.persistence.jpa.adapter

import com.quantjumpstock.core.adapter.output.persistence.jpa.entity.StrategyEntity
import com.quantjumpstock.core.domain.model.Strategy

/**
 * Extension Functions - 간단한 매핑용
 *
 * ✅ 1:1 필드 매핑에 적합
 * ❌ 연관관계 처리 불가
 */
fun Strategy.toEntity(): StrategyEntity {
    return StrategyEntity(
        id = id,
        name = name,
        strategyType = type,
        status = status,
        createdAt = createdAt
    )
}

fun StrategyEntity.toDomain(): Strategy {
    return Strategy(
        id = id,
        name = name,
        type = strategyType,
        status = status,
        createdAt = createdAt
    )
}
```

### 2.3 복잡한 매핑 예시

**시나리오**: 전략이 Category와 연관관계를 가질 때

```kotlin
@Component
class StrategyMapper(
    private val categoryJpaRepository: CategoryJpaRepository
) {

    fun toEntity(domain: Strategy): StrategyEntity {
        val categoryEntity = domain.categoryId?.let {
            categoryJpaRepository.findById(it).orElseThrow {
                IllegalArgumentException("Category not found: $it")
            }
        }

        return StrategyEntity(
            id = domain.id,
            name = domain.name,
            strategyType = domain.type,
            status = domain.status,
            category = categoryEntity,  // 연관관계 설정
            createdAt = domain.createdAt
        )
    }

    fun toDomain(entity: StrategyEntity): Strategy {
        return Strategy(
            id = entity.id,
            name = entity.name,
            type = entity.strategyType,
            status = entity.status,
            categoryId = entity.category?.id,  // 연관관계 ID만 포함
            createdAt = entity.createdAt
        )
    }
}
```

---

## 3. 테스트 패턴

### 3.1 Adapter 통합 테스트

**위치**: `src/test/kotlin/.../adapter/output/persistence/jpa/adapter/`

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
 * ✅ 실제 PostgreSQL 사용 (Testcontainers)
 * ✅ JPA 동작 검증
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

    "전략을 저장하고 조회할 수 있다" {
        // Given
        val strategy = Strategy(
            id = null,
            name = "테스트 전략",
            type = StrategyType.MOMENTUM,
            status = StrategyStatus.DRAFT
        )

        // When
        val saved = adapter.save(strategy)

        // Then
        saved.id shouldNotBe null
        saved.name shouldBe "테스트 전략"
        saved.type shouldBe StrategyType.MOMENTUM
        saved.status shouldBe StrategyStatus.DRAFT
    }

    "ID로 전략을 조회할 수 있다" {
        // Given
        val strategy = Strategy(
            id = null,
            name = "조회 테스트",
            type = StrategyType.VALUE,
            status = StrategyStatus.ACTIVE
        )
        val saved = adapter.save(strategy)

        // When
        val found = adapter.findById(saved.id!!)

        // Then
        found shouldNotBe null
        found!!.id shouldBe saved.id
        found.name shouldBe "조회 테스트"
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

### 3.2 Mapper 단위 테스트

```kotlin
package com.quantjumpstock.core.adapter.output.persistence.jpa.mapper

import com.quantjumpstock.core.adapter.output.persistence.jpa.entity.StrategyEntity
import com.quantjumpstock.core.domain.model.Strategy
import com.quantjumpstock.core.domain.model.StrategyStatus
import com.quantjumpstock.core.domain.model.StrategyType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk

class StrategyMapperTest : StringSpec({

    val mapper = StrategyMapper(mockk())

    "도메인 모델을 엔티티로 변환할 수 있다" {
        // Given
        val domain = Strategy(
            id = 1L,
            name = "테스트",
            type = StrategyType.MOMENTUM,
            status = StrategyStatus.ACTIVE
        )

        // When
        val entity = mapper.toEntity(domain)

        // Then
        entity.id shouldBe 1L
        entity.name shouldBe "테스트"
        entity.strategyType shouldBe StrategyType.MOMENTUM
        entity.status shouldBe StrategyStatus.ACTIVE
    }

    "엔티티를 도메인 모델로 변환할 수 있다" {
        // Given
        val entity = StrategyEntity(
            id = 2L,
            name = "엔티티 테스트",
            strategyType = StrategyType.VALUE,
            status = StrategyStatus.DRAFT
        )

        // When
        val domain = mapper.toDomain(entity)

        // Then
        domain.id shouldBe 2L
        domain.name shouldBe "엔티티 테스트"
        domain.type shouldBe StrategyType.VALUE
        domain.status shouldBe StrategyStatus.DRAFT
    }
})
```

---

## 4. 실전 예제

### 4.1 MongoDB Adapter 예시

**구조**:
```
adapter/output/persistence/mongodb/
├── document/
│   └── StockDocument.kt           # MongoDB Document
├── repository/
│   └── StockMongoRepository.kt    # Spring Data Mongo Repository
├── adapter/
│   └── StockPersistenceAdapter.kt # 도메인 포트 구현
└── mapper/
    └── StockMapper.kt             # 매핑 로직
```

**MongoDB Document**:
```kotlin
package com.quantjumpstock.core.adapter.output.persistence.mongodb.document

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.time.LocalDate

/**
 * MongoDB Document - 시계열 주식 데이터
 *
 * ⚠️ Infrastructure 계층에만 존재
 */
@Document(collection = "stocks")
data class StockDocument(
    @Id
    val id: String? = null,

    @Field("symbol")
    val symbol: String,

    @Field("stock_name")
    val stockName: String,

    @Field("close_price")
    val closePrice: Double,

    @Field("date")
    val date: LocalDate
)
```

**Domain Model**:
```kotlin
package com.quantjumpstock.core.domain.model

import java.time.LocalDate

/**
 * 순수 도메인 모델 - MongoDB 어노테이션 없음
 */
data class Stock(
    val id: String?,
    val symbol: String,
    val stockName: String,
    val closePrice: Double,
    val date: LocalDate
) {
    init {
        require(symbol.isNotBlank()) { "종목 코드는 필수입니다" }
        require(closePrice >= 0) { "주가는 0 이상이어야 합니다" }
    }
}
```

**Persistence Adapter**:
```kotlin
package com.quantjumpstock.core.adapter.output.persistence.mongodb.adapter

import com.quantjumpstock.core.adapter.output.persistence.mongodb.repository.StockMongoRepository
import com.quantjumpstock.core.adapter.output.persistence.mongodb.mapper.StockMapper
import com.quantjumpstock.core.domain.model.Stock
import com.quantjumpstock.core.domain.port.output.StockRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class StockPersistenceAdapter(
    private val mongoRepository: StockMongoRepository,
    private val mapper: StockMapper
) : StockRepository {

    override fun save(stock: Stock): Stock {
        val document = mapper.toDocument(stock)
        val saved = mongoRepository.save(document)
        return mapper.toDomain(saved)
    }

    override fun findBySymbolAndDate(symbol: String, date: LocalDate): Stock? {
        return mongoRepository.findBySymbolAndDate(symbol, date)
            ?.let { mapper.toDomain(it) }
    }

    override fun findBySymbolBetweenDates(
        symbol: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Stock> {
        return mongoRepository.findBySymbolAndDateBetween(symbol, startDate, endDate)
            .map { mapper.toDomain(it) }
    }
}
```

### 4.2 Application Service 사용 예시

```kotlin
package com.quantjumpstock.core.application.strategy

import com.quantjumpstock.core.domain.model.Strategy
import com.quantjumpstock.core.domain.model.StrategyStatus
import com.quantjumpstock.core.domain.port.output.StrategyRepository
import com.quantjumpstock.core.domain.event.DomainEventPublisher
import com.quantjumpstock.core.domain.event.StrategyCreatedEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Application Service
 *
 * ✅ 도메인 포트에만 의존
 * ❌ JPA/MongoDB 직접 의존 금지
 */
@Service
@Transactional
class StrategyService(
    private val strategyRepository: StrategyRepository,  // ✅ 도메인 포트
    private val eventPublisher: DomainEventPublisher     // ✅ 도메인 포트
) {

    fun createStrategy(request: CreateStrategyRequest): Strategy {
        val strategy = Strategy(
            id = null,
            name = request.name,
            type = request.type,
            status = StrategyStatus.DRAFT
        )

        val saved = strategyRepository.save(strategy)
        eventPublisher.publish(StrategyCreatedEvent(saved.id!!))

        return saved
    }

    fun activateStrategy(id: Long): Strategy {
        val strategy = strategyRepository.findById(id)
            ?: throw StrategyNotFoundException("전략을 찾을 수 없습니다: $id")

        val activated = strategy.activate()  // 도메인 로직
        return strategyRepository.save(activated)
    }

    fun findActiveStrategies(): List<Strategy> {
        return strategyRepository.findAllActive()
    }
}
```

---

## 5. 체크리스트

### 새 Adapter 작성 시

- [ ] Domain Model이 순수 Kotlin인가? (어노테이션 없음)
- [ ] Domain Port가 도메인 타입만 사용하는가?
- [ ] JPA Entity가 `adapter/output/persistence/jpa/entity/`에 위치하는가?
- [ ] Adapter가 도메인 포트를 구현하는가?
- [ ] Mapper가 양방향 변환을 제공하는가?
- [ ] Application Service가 인프라에 의존하지 않는가?
- [ ] 통합 테스트가 작성되었는가?
- [ ] ArchUnit 테스트를 통과하는가?

### 리팩토링 시

- [ ] 기존 통합 테스트가 모두 통과하는가?
- [ ] 도메인 로직이 Domain 계층으로 이동했는가?
- [ ] Application 계층에서 JPA import를 제거했는가?
- [ ] 새 단위 테스트가 추가되었는가?
