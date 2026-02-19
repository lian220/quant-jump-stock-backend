# QuantiQ Core Documentation

QuantiQ 시스템의 기술 문서 모음입니다.

---

## 📁 문서 구조

```
docs/
├── README.md                    # 문서 인덱스 (현재 파일)
├── database/                    # 데이터베이스 관련 문서
│   ├── 스키마.md               # DB 스키마 정의 및 설명
│   └── 관계.md        # 연관 관계 및 데이터 흐름
├── api/                        # API 문서 (예정)
│   ├── REST_API.md            # REST API 명세
│   └── SWAGGER.md             # Swagger/OpenAPI 가이드
└── architecture/              # 아키텍처 문서 (예정)
    ├── HEXAGONAL.md           # 헥사고날 아키텍처 설명
    ├── SCHEDULER.md           # 스케줄러 시스템
    └── VERTEX_AI.md           # Vertex AI 통합
```

---

## 📚 주요 문서

### Database

#### [Database Schema](./database/스키마.md)
- PostgreSQL 및 MongoDB 스키마 정의
- 엔티티 및 컬렉션 구조
- 인덱스 및 성능 최적화
- 보안 고려사항

#### [Database Relationships](./database/관계.md)
- 테이블 간 연관 관계
- 데이터 흐름 다이어그램
- 트랜잭션 경계
- 동시성 제어

---

## 🏗️ 시스템 아키텍처

### 헥사고날 아키텍처 (Ports & Adapters)

```
┌─────────────────────────────────────────────────────────┐
│                   Application Layer                     │
│  ┌────────────────────────────────────────────────────┐ │
│  │              Use Cases (Business Logic)            │ │
│  └────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
         ▲                                        │
         │                                        ▼
┌────────────────────┐                ┌────────────────────┐
│   Input Adapters   │                │  Output Adapters   │
│  ┌──────────────┐  │                │  ┌──────────────┐  │
│  │ REST API     │  │                │  │ PostgreSQL   │  │
│  │ Scheduler    │  │                │  │ MongoDB      │  │
│  │ Web UI       │  │                │  │ KIS API      │  │
│  └──────────────┘  │                │  │ Slack API    │  │
└────────────────────┘                │  │ Vertex AI    │  │
                                      │  └──────────────┘  │
                                      └────────────────────┘
```

---

## 🗄️ 데이터베이스 전략

### Polyglot Persistence

#### PostgreSQL
- **용도**: 트랜잭션 데이터, 사용자 정보, 거래 내역, **주식 메타데이터**
- **강점**: ACID 보장, 복잡한 JOIN, 외래키 무결성
- **사용 테이블**:
  - users, user_kis_accounts, kis_tokens
  - trading_configs, account_balances
  - trades, trade_signals_executed
  - **stocks** ✨ (2026-02-01 MongoDB → PostgreSQL 마이그레이션 완료)

#### MongoDB
- **용도**: 분석 데이터, 시계열 데이터, 예측 결과
- **강점**: 유연한 스키마, 대용량 읽기 성능, 수평 확장
- **사용 컬렉션**:
  - daily_stock_data, economic_data
  - stock_analysis_results, sentiment_analysis
  - stock_recommendations, prediction_results

---

## 🔐 보안

### 민감 정보 보호
- **KIS App Secret**: AES-256 암호화 저장
- **Access Token**: 만료 시간 관리, 자동 갱신
- **사용자 비밀번호**: BCrypt 해싱

### API 인증
- JWT 기반 인증 (예정)
- 사용자별 데이터 격리

---

## 🚀 성능 최적화

### PostgreSQL
- **인덱스**: 조회 성능 최적화
  - `(user_id, account_type)` UNIQUE
  - `created_at` DESC
- **낙관적 락**: 동시성 제어 (@Version)

### MongoDB
- **인덱스**: 복합 인덱스 활용
  - `(ticker, date)` UNIQUE
  - `composite_score` DESC
- **샤딩**: 대용량 데이터 분산 (예정)

---

## 📊 데이터 마이그레이션 히스토리

### Stock 데이터: MongoDB → PostgreSQL (2026-02-01)

**배경**:
- `stocks` 컬렉션은 주식 메타데이터로 거의 변경되지 않는 정적 참조 데이터
- MongoDB의 유연한 스키마 장점이 필요 없음
- RDB의 인덱싱, FK 제약조건, JOIN 성능이 더 유리

**마이그레이션 결과**:
- ✅ PostgreSQL `stocks` 테이블 생성 (V6 마이그레이션)
- ✅ 35개 stocks 데이터 이전 완료
- ✅ 초기 데이터 SQL 생성 (V7 마이그레이션)

**관련 문서**:
- [Stock 마이그레이션 상세 문서](../claudedocs/Stock_마이그레이션_MongoDB_to_PostgreSQL.md)
- [TODO: 비즈니스 로직 적용](../할일.md#stock-데이터-postgresql-마이그레이션-후속-작업)

**다음 단계**:
- [ ] `StockPersistenceAdapter` 구현 (dual-write 지원)
- [ ] 기존 Stock 사용 지점 RDB로 전환
- [ ] 충분한 검증 후 MongoDB `stocks` 컬렉션 제거

---

## 📋 TODO

### 문서화 작업
- [ ] REST API 명세서 작성
- [ ] 아키텍처 다이어그램 추가
- [ ] 스케줄러 시스템 문서화
- [ ] Vertex AI 통합 가이드
- [ ] 배포 및 운영 가이드

### 기능 개발
- [ ] Vertex AI CustomJob 파라미터 전달 기능 ([할일.md](../할일.md))
- [ ] JWT 인증 구현
- [ ] API Rate Limiting
- [ ] 모니터링 대시보드

---

## 🔗 관련 리소스

### 내부 문서
- [TODO List](../할일.md)
- [Database Schema](./database/스키마.md)
- [Database Relationships](./database/관계.md)

### 외부 참고
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Google Vertex AI](https://cloud.google.com/vertex-ai/docs)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [MongoDB Documentation](https://www.mongodb.com/docs/)

---

## 📝 문서 작성 가이드

### 마크다운 규칙
- 제목은 명확하고 계층 구조 유지
- 코드 블록에 언어 지정 (```kotlin, ```sql, ```javascript)
- 다이어그램은 ASCII 아트 또는 Mermaid 사용
- 테이블로 정보 정리

### 문서 업데이트
- 코드 변경 시 관련 문서 함께 업데이트
- 버전 정보 명시 (필요 시)
- 날짜 표기: YYYY-MM-DD 형식

---

## 👥 기여자

문서 개선 제안 및 오류 수정은 언제든 환영합니다!

---

**Last Updated**: 2026-02-01
