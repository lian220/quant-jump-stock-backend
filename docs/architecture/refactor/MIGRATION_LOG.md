# Backend Architecture Migration Log

> **목적**: Hexagonal Architecture 마이그레이션 진행 상황 기록
> **기간**: 2026-02-04 ~ (14주 예정)
> **현재 상태**: 계획 수립 완료

---

## 📊 전체 진행 상황

| Phase | 기간 | 상태 | 진행률 |
|-------|------|------|--------|
| Phase 1: 기반 구축 | Week 1-4 | ✅ 완료 | 100% |
| Phase 2: 핵심 서비스 마이그레이션 | Week 5-10 | 🔄 진행 중 | 25% |
| Phase 3: 최적화 및 강화 | Week 11-14 | 🔲 계획됨 | 0% |

**범례**: 🔲 계획됨 | 🔄 진행 중 | ✅ 완료 | ⚠️ 지연 | ❌ 차단됨

---

## Phase 1: 기반 구축 (Week 1-4)

### Week 1: 테스트 인프라 구축

**목표**: 테스트 프레임워크 설정 및 ArchUnit 검증 기반 마련

**상태**: ✅ 완료

#### 작업 항목

| 작업 | 상태 | 담당자 | 완료일 | 비고 |
|------|------|--------|--------|------|
| build.gradle.kts 의존성 추가 | ✅ | Claude | 2026-02-04 | Kotest, MockK, ArchUnit, Testcontainers |
| spring-boot-starter-webflux 제거 | ⚠️ | - | - | Phase 3에서 처리 (안정성 우선) |
| ArchitectureTest.kt 생성 | ✅ | Claude | 2026-02-04 | 계층 검증 규칙 3개 |
| 테스트 픽스처 생성 | 🔲 | - | - | Week 2에서 진행 |
| 기준선 아키텍처 위반 측정 | ✅ | Claude | 2026-02-04 | 3개 규칙 모두 실패 |

#### 산출물

- [x] `build.gradle.kts` 업데이트 (Kotest, MockK, ArchUnit, Testcontainers, SpringMockK)
- [x] `src/test/kotlin/com/quantjumpstock/core/architecture/ArchitectureTest.kt`
- [ ] `src/test/kotlin/com/quantjumpstock/core/fixtures/StrategyFixture.kt`
- [x] 기준선 위반 리포트

#### 기준선 위반 측정 결과 (2026-02-04)

| 규칙 | 상태 | 설명 |
|------|------|------|
| layeredArchitecture | ❌ FAIL | Application이 Domain만 접근해야 하나 Persistence 접근 |
| domainShouldNotDependOnAdapter | ❌ FAIL | Domain이 Adapter에 의존 |
| applicationShouldNotDependOnPersistence | ❌ FAIL | 9개 서비스가 JPA Entity 직접 import |

**위반 서비스 목록**:
- StrategyService, AutoTradingService, UserKisAccountService
- CategoryService, AdminStrategyService, AuthService
- OAuthService, BalanceService, MarketplaceService

**위반 현황**: 21개 JPA Entity, 9개 Application 서비스 위반

#### 이슈 및 결정사항

- [2026-02-04] 테스트 의존성 추가 완료
- [2026-02-04] ArchUnit 3개 규칙 정의, 모두 실패 상태 (기준선)
- [2026-02-04] webflux 제거는 Phase 3에서 진행 (무중단 변경 원칙)
- [2026-02-04] domain/port 디렉토리 없음 → Week 2에서 생성 필요

#### 주간 회고

**잘한 점**:
- 테스트 인프라 기반 구축 완료
- 명확한 기준선 측정으로 개선 추적 가능

**개선할 점**:
- 테스트 픽스처 생성 지연

**다음 주 조치사항**:
- Week 2: 순수 도메인 모델 및 포트 생성

---

### Week 2: 순수 도메인 모델 생성

**목표**: 인프라 의존성 없는 순수 도메인 모델 및 포트 정의

**상태**: ✅ 완료

#### 작업 항목

| 작업 | 상태 | 담당자 | 완료일 | 비고 |
|------|------|--------|--------|------|
| Strategy 도메인 모델 생성 | ✅ | Claude | 2026-02-04 | 순수 Kotlin, 비즈니스 로직 포함 |
| Account 도메인 모델 생성 | ✅ | Claude | 2026-02-04 | 잔액 검증 로직 |
| Trade 도메인 모델 생성 | ✅ | Claude | 2026-02-04 | 거래 검증 로직 |
| StrategyCategory 도메인 모델 생성 | ✅ | Claude | 2026-02-04 | 추가 |
| StrategyRepository 포트 정의 | ✅ | Claude | 2026-02-04 | domain/port/output/ |
| AccountRepository 포트 정의 | ✅ | Claude | 2026-02-04 | |
| TradeRepository 포트 정의 | ✅ | Claude | 2026-02-04 | |
| StrategyCategoryRepository 포트 정의 | ✅ | Claude | 2026-02-04 | |
| Enum 이동 (adapter → domain) | ✅ | Claude | 2026-02-04 | StrategyStatus, RebalanceFrequency, TradeSide, TradeStatus |
| 도메인 모델 단위 테스트 | ✅ | Claude | 2026-02-04 | Kotest 사용 |

#### 산출물

- [x] `domain/model/strategy/Strategy.kt` (순수 도메인 모델)
- [x] `domain/model/strategy/StrategyStatus.kt`
- [x] `domain/model/strategy/RebalanceFrequency.kt`
- [x] `domain/model/strategy/StrategyCategory.kt`
- [x] `domain/model/trading/Account.kt`
- [x] `domain/model/trading/Trade.kt`
- [x] `domain/model/trading/TradeSide.kt`
- [x] `domain/model/trading/TradeStatus.kt`
- [x] `domain/port/output/StrategyRepository.kt`
- [x] `domain/port/output/StrategyCategoryRepository.kt`
- [x] `domain/port/output/AccountRepository.kt`
- [x] `domain/port/output/TradeRepository.kt`
- [x] 도메인 계층 단위 테스트 (Kotest) - StrategyTest, TradeTest, AccountTest

#### 이슈 및 결정사항

- [2026-02-04] Strategy 도메인 모델에 상태 전이 로직 포함 (submitForReview, approve, publish 등)
- [2026-02-04] Account 도메인 모델에 잔액 관련 비즈니스 로직 포함 (lockCash, executeBuy 등)
- [2026-02-04] Trade 도메인 모델에 체결/실패/취소 로직 포함
- [2026-02-04] 기존 adapter의 Enum과 별도로 domain에 순수 Enum 생성 (점진적 마이그레이션)

#### 주간 회고

**잘한 점**:
- 순수 Kotlin 도메인 모델 생성 완료
- 비즈니스 로직을 도메인에 캡슐화
- Kotest 단위 테스트로 검증

**개선할 점**:
- Order 도메인 모델 미생성 (추후 필요시 추가)

**다음 주 조치사항**:
- Week 3: Persistence Adapter 구현

---

### Week 3: Persistence Adapter 구현

**목표**: JPA/MongoDB Adapter 구현 및 매핑 로직 작성

**상태**: ✅ 완료

#### 작업 항목

| 작업 | 상태 | 담당자 | 완료일 | 비고 |
|------|------|--------|--------|------|
| StrategyPersistenceAdapterV2 구현 | ✅ | Claude | 2026-02-04 | 도메인 포트 구현 |
| StrategyMapper 구현 | ✅ | Claude | 2026-02-04 | toEntity/toDomain |
| AccountPersistenceAdapter 구현 | ✅ | Claude | 2026-02-04 | 도메인 포트 구현 |
| AccountMapper 구현 | ✅ | Claude | 2026-02-04 | toEntity/toDomain |
| TradePersistenceAdapter 구현 | ✅ | Claude | 2026-02-04 | 도메인 포트 구현 |
| TradeMapper 구현 | ✅ | Claude | 2026-02-04 | toEntity/toDomain |
| StockPersistenceAdapter 구현 | 🔲 | - | - | MongoDB (Phase 2) |
| Adapter 통합 테스트 | 🔲 | - | - | Testcontainers (Phase 2) |

#### 산출물

- [x] `adapter/output/persistence/jpa/adapter/StrategyPersistenceAdapterV2.kt`
- [x] `adapter/output/persistence/jpa/adapter/AccountPersistenceAdapter.kt`
- [x] `adapter/output/persistence/jpa/adapter/TradePersistenceAdapter.kt`
- [x] `adapter/output/persistence/jpa/mapper/StrategyMapper.kt`
- [x] `adapter/output/persistence/jpa/mapper/AccountMapper.kt`
- [x] `adapter/output/persistence/jpa/mapper/TradeMapper.kt`
- [ ] `adapter/output/persistence/mongodb/adapter/StockPersistenceAdapter.kt` (Phase 2)
- [ ] Adapter 통합 테스트 (Testcontainers) (Phase 2)

#### 이슈 및 결정사항

- [2026-02-04] Mapper를 Component로 구현 (DI 활용)
- [2026-02-04] 기존 StrategyPersistenceAdapter 유지, V2로 새 버전 생성 (점진적 마이그레이션)
- [2026-02-04] JPA Repository에 Nullable 메서드 추가 (findByUserIdNullable) - 기존 코드 호환성 유지

#### 주간 회고

**잘한 점**:
- Hexagonal Architecture 준수하는 새 Adapter 구현
- Mapper 패턴으로 관심사 분리
- 기존 코드 영향 없이 점진적 마이그레이션 가능

**개선할 점**:
- MongoDB Adapter 미구현
- 통합 테스트 미작성

**다음 주 조치사항**:
- Week 4: 파일럿 서비스 리팩토링 (AnalysisService)

---

### Week 4: 추가 도메인 모델 및 Adapter 구현

**목표**: 서비스 리팩토링을 위한 추가 도메인 모델 및 Adapter 구현

**상태**: ✅ 완료

#### 작업 항목

| 작업 | 상태 | 담당자 | 완료일 | 비고 |
|------|------|--------|--------|------|
| User 도메인 모델 생성 | ✅ | Claude | 2026-02-04 | User, UserStatus, UserRole |
| UserRepository 포트 생성 | ✅ | Claude | 2026-02-04 | |
| UserMapper 구현 | ✅ | Claude | 2026-02-04 | Entity ↔ Domain 변환 |
| UserPersistenceAdapter 구현 | ✅ | Claude | 2026-02-04 | 도메인 포트 구현 |
| StrategyCategoryMapper 구현 | ✅ | Claude | 2026-02-04 | Entity ↔ Domain 변환 |
| StrategyCategoryPersistenceAdapter 구현 | ✅ | Claude | 2026-02-04 | 도메인 포트 구현 |
| AnalysisManagementService 확인 | ✅ | Claude | 2026-02-04 | 이미 도메인 포트 사용 중 |

#### 산출물

- [x] `domain/model/user/User.kt`
- [x] `domain/model/user/UserStatus.kt`, `UserRole.kt`
- [x] `domain/port/output/UserRepository.kt`
- [x] `adapter/output/persistence/jpa/mapper/UserMapper.kt`
- [x] `adapter/output/persistence/jpa/adapter/UserPersistenceAdapter.kt`
- [x] `adapter/output/persistence/jpa/mapper/StrategyCategoryMapper.kt`
- [x] `adapter/output/persistence/jpa/adapter/StrategyCategoryPersistenceAdapter.kt`

#### 이슈 및 결정사항

- [2026-02-04] AnalysisManagementService는 이미 도메인 포트(MessagePublisher, NotificationSender)를 사용하여 Hexagonal Architecture 준수
- [2026-02-04] StrategyService 리팩토링은 Controller 변경 필요 → Phase 2에서 진행
- [2026-02-04] User 도메인 모델 생성 완료, OAuth 관련 필드는 제외 (보안 관련)

#### 주간 회고

**잘한 점**:
- 추가 도메인 모델 및 Adapter 구현 완료
- 기존 코드 영향 없이 새로운 구조 추가

**개선할 점**:
- 실제 서비스 리팩토링 미완료

**다음 주 조치사항**:
- Phase 2: 핵심 서비스 마이그레이션 (StrategyService, TradingService 등)

---

### Phase 1 요약

**기간**: Week 1-4
**최종 상태**: 🔲

**성공 지표 달성 여부**:

| 지표 | 목표 | 실제 | 달성 |
|------|------|------|------|
| ArchUnit 위반 감소 | 10% | - | 🔲 |
| 단위 테스트 커버리지 | 30%+ | - | 🔲 |
| 통합 테스트 통과율 | 100% | - | 🔲 |
| 빌드 시간 | < 2분 | - | 🔲 |

**전체 회고**:
-

**Phase 2 준비사항**:
-

---

## Phase 2: 핵심 서비스 마이그레이션 (Week 5-10)

### Week 5-6: 거래 서비스 리팩토링

**목표**: AutoTradingService, TradingService 도메인 포트 전환

**상태**: 🔄 진행 중

#### 작업 항목

| 작업 | 상태 | 담당자 | 완료일 | 비고 |
|------|------|--------|--------|------|
| StrategyService 리팩토링 | ✅ | Claude | 2026-02-04 | 도메인 포트로 전환 완료 |
| StrategyDto 도메인 enum 전환 | ✅ | Claude | 2026-02-04 | JPA enum → Domain enum |
| TradingConfig 도메인 모델 생성 | ✅ | Claude | 2026-02-04 | 거래 설정 도메인 모델 |
| TradingConfigRepository 포트/어댑터 | ✅ | Claude | 2026-02-04 | 완전한 구현 |
| TradeSignalExecuted 도메인 모델 생성 | ✅ | Claude | 2026-02-04 | 신호 실행 기록 |
| TradeSignalExecutedRepository 포트/어댑터 | ✅ | Claude | 2026-02-04 | 완전한 구현 |
| TradeSignal, ExecutionDecision enum | ✅ | Claude | 2026-02-04 | 도메인 Enum |
| TradeRepository findRecentTrades 추가 | ✅ | Claude | 2026-02-04 | 최근 거래 조회 |
| AutoTradingService 리팩토링 | 🔲 | - | - | JPA 의존성 제거 필요 |
| BalanceService 리팩토링 | 🔲 | - | - | JPA 의존성 제거 필요 |
| 동시성 테스트 작성 | 🔲 | - | - | 100개 동시 작업 |
| Optimistic Locking 검증 | 🔲 | - | - | 계좌 잔액 업데이트 |
| 거래 실행 로직 단위 테스트 | 🔲 | - | - | 100% 커버리지 |

#### 산출물

- [x] 리팩토링된 `application/strategy/StrategyService.kt` (도메인 포트 사용)
- [x] `application/strategy/StrategyDto.kt` (도메인 enum 사용)
- [x] `domain/model/trading/TradingConfig.kt`
- [x] `domain/model/trading/TradeSignalExecuted.kt`
- [x] `domain/model/trading/TradeSignal.kt`
- [x] `domain/model/trading/ExecutionDecision.kt`
- [x] `domain/port/output/TradingConfigRepository.kt`
- [x] `domain/port/output/TradeSignalExecutedRepository.kt`
- [x] `adapter/output/persistence/jpa/mapper/TradingConfigMapper.kt`
- [x] `adapter/output/persistence/jpa/mapper/TradeSignalExecutedMapper.kt`
- [x] `adapter/output/persistence/jpa/adapter/TradingConfigPersistenceAdapter.kt`
- [x] `adapter/output/persistence/jpa/adapter/TradeSignalExecutedPersistenceAdapter.kt`
- [ ] 리팩토링된 `application/trading/AutoTradingService.kt`
- [ ] `AccountConcurrencyTest.kt` (동시성 테스트)
- [ ] 중요 거래 로직 100% 단위 테스트

#### 이슈 및 결정사항

- [2026-02-04] StrategyService 완전 리팩토링 완료 - JPA Entity import 0개
- [2026-02-04] AutoTradingService는 복잡도가 높아 추가 도메인 모델/포트 필요
- [2026-02-04] TradingConfig, TradeSignalExecuted 도메인 모델 및 포트 생성 완료
- [2026-02-04] ArchUnit 테스트 여전히 3개 실패 (다른 서비스들 미리팩토링)

#### 위반 현황 (2026-02-04)

**Application 계층 JPA 의존성 현황**:
| 서비스 | JPA Import 수 | 상태 |
|--------|---------------|------|
| StrategyService | 0 | ✅ 리팩토링 완료 |
| AutoTradingService | 7 | 🔲 미완료 |
| BalanceService | 5 | 🔲 미완료 |
| AuthService | 4 | 🔲 미완료 |
| OAuthService | 5 | 🔲 미완료 |
| CategoryService | 2 | 🔲 미완료 |
| AdminStrategyService | 4 | 🔲 미완료 |
| AdminStrategyDto | 2 | 🔲 미완료 |
| MarketplaceService | 3 | 🔲 미완료 |
| UserKisAccountService | 다수 | 🔲 미완료 |
| StockRecommendationService | MongoDB | 🔲 미완료 |

#### 주간 회고

**잘한 점**:
- StrategyService 완전 리팩토링 성공 (JPA 의존성 0)
- AutoTradingService 리팩토링을 위한 도메인 모델/포트 인프라 구축
- 점진적 마이그레이션 전략 유지

**개선할 점**:
- AutoTradingService 복잡도로 인해 완전 리팩토링 미완료
- ArchUnit 테스트 여전히 실패 (다른 서비스 의존성)

**다음 주 조치사항**:
- AutoTradingService 리팩토링 계속
- BalanceService 리팩토링
- AuthService/OAuthService 리팩토링

---

### Week 7-8: 전략 및 분석 서비스

**목표**: StrategyService, BacktestService 리팩토링

**상태**: 🔲 시작 전

#### 작업 항목

| 작업 | 상태 | 담당자 | 완료일 | 비고 |
|------|------|--------|--------|------|
| StrategyService 리팩토링 | 🔲 | - | - | |
| BacktestService 리팩토링 | 🔲 | - | - | |
| 백테스트 계산 도메인 서비스 | 🔲 | - | - | |
| Property-based testing 추가 | 🔲 | - | - | CAGR, Sharpe ratio |

#### 산출물

#### 이슈 및 결정사항

#### 주간 회고

---

### Week 9-10: 사용자 및 스케줄러 서비스

**목표**: 나머지 서비스 리팩토링 및 설정 정리

**상태**: 🔲 시작 전

#### 작업 항목

| 작업 | 상태 | 담당자 | 완료일 | 비고 |
|------|------|--------|--------|------|
| UserService 리팩토링 | 🔲 | - | - | |
| SchedulerService 리팩토링 | 🔲 | - | - | |
| Redis 의존성 정리 | 🔲 | - | - | 추가 또는 설정 제거 |
| CORS 설정 추가 | 🔲 | - | - | Frontend :3000, Backoffice :4000 |
| 전체 Application 계층 검증 | 🔲 | - | - | JPA import 0건 확인 |

#### 산출물

#### 이슈 및 결정사항

#### 주간 회고

---

### Phase 2 요약

**기간**: Week 5-10
**최종 상태**: 🔲

**성공 지표 달성 여부**:

| 지표 | 목표 | 실제 | 달성 |
|------|------|------|------|
| ArchUnit 위반 | 0 | - | 🔲 |
| 단위 테스트 커버리지 | 75%+ | - | 🔲 |
| Application → JPA imports | 0 | - | 🔲 |
| 통합 테스트 통과율 | 100% | - | 🔲 |

**전체 회고**:
-

**Phase 3 준비사항**:
-

---

## Phase 3: 최적화 및 강화 (Week 11-14)

### Week 11: 설정 정리

**목표**: 프로덕션 배포 준비 (Security, Kafka, 모니터링)

**상태**: 🔲 시작 전

#### 작업 항목

| 작업 | 상태 | 담당자 | 완료일 | 비고 |
|------|------|--------|--------|------|
| Security 환경변수 토글 추가 | 🔲 | - | - | app.security.enabled |
| Kafka 수동 커밋 설정 | 🔲 | - | - | MANUAL_IMMEDIATE |
| Spring Actuator 설정 | 🔲 | - | - | 헬스체크, 메트릭 |
| 로깅 설정 개선 | 🔲 | - | - | |

#### 산출물

#### 이슈 및 결정사항

#### 주간 회고

---

### Week 12: 성능 테스트

**목표**: 부하 테스트 및 성능 최적화

**상태**: 🔲 시작 전

#### 작업 항목

| 작업 | 상태 | 담당자 | 완료일 | 비고 |
|------|------|--------|--------|------|
| k6/Gatling 부하 테스트 작성 | 🔲 | - | - | 100 req/sec |
| 데이터베이스 쿼리 프로파일링 | 🔲 | - | - | Hibernate statistics |
| N+1 쿼리 제거 | 🔲 | - | - | |
| Redis 캐싱 추가 | 🔲 | - | - | 주식 시세 등 |
| P95/P99 latency 측정 | 🔲 | - | - | 목표: < 200ms / < 500ms |

#### 산출물

#### 이슈 및 결정사항

#### 주간 회고

---

### Week 13: 보안 강화

**목표**: JWT 인증, RBAC 구현

**상태**: 🔲 시작 전

#### 작업 항목

| 작업 | 상태 | 담당자 | 완료일 | 비고 |
|------|------|--------|--------|------|
| JWT 인증 구현 | 🔲 | - | - | |
| RBAC 권한 체계 구현 | 🔲 | - | - | |
| Security 통합 테스트 | 🔲 | - | - | |
| OWASP Top 10 점검 | 🔲 | - | - | |

#### 산출물

#### 이슈 및 결정사항

#### 주간 회고

---

### Week 14: 문서화 및 교육

**목표**: 팀 지식 전달 및 문서 완성

**상태**: 🔲 시작 전

#### 작업 항목

| 작업 | 상태 | 담당자 | 완료일 | 비고 |
|------|------|--------|--------|------|
| CLAUDE.md 업데이트 | 🔲 | - | - | 아키텍처 규칙 추가 |
| Adapter 패턴 가이드 보완 | 🔲 | - | - | |
| 새 서비스 작성 가이드 | 🔲 | - | - | |
| 팀 교육 세션 진행 | 🔲 | - | - | |
| Migration Log 정리 | 🔲 | - | - | |

#### 산출물

#### 이슈 및 결정사항

#### 주간 회고

---

### Phase 3 요약

**기간**: Week 11-14
**최종 상태**: 🔲

**성공 지표 달성 여부**:

| 지표 | 목표 | 실제 | 달성 |
|------|------|------|------|
| P95 latency | < 200ms | - | 🔲 |
| 보안 활성화 | 프로덕션 O | - | 🔲 |
| 테스트 커버리지 | 80%+ | - | 🔲 |
| ArchUnit 점수 | 100/100 | - | 🔲 |

**전체 회고**:
-

---

## 최종 결과

### 전체 프로젝트 요약

**기간**: 2026-02-04 ~ (14주)
**참여 인원**: -
**총 작업 시간**: -

### 최종 지표

| 지표 | 시작 | 종료 | 개선 |
|------|------|------|------|
| ArchUnit 위반 | ~27건 | - | - |
| 단위 테스트 커버리지 | 0% | - | - |
| 통합 테스트 커버리지 | ~10% | - | - |
| Application → JPA imports | 11개 서비스 | - | - |
| Domain 모델 오염 | 5 files | - | - |
| P95 latency | N/A | - | - |
| 빌드 시간 | ~1분 | - | - |

### 주요 성과

1. **아키텍처 개선**:
   -

2. **테스트 품질**:
   -

3. **성능 최적화**:
   -

4. **보안 강화**:
   -

### 배운 점

**기술적 교훈**:
-

**프로세스 교훈**:
-

**팀 협업**:
-

### 향후 계획

1. **단기 (1-3개월)**:
   -

2. **중기 (3-6개월)**:
   -

3. **장기 (6-12개월)**:
   - Modular Monolith 전환 검토 (팀 5-7명 이상 시)
   - Event Sourcing 도입 검토
