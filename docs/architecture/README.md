# 시스템 아키텍처

Quantiq의 백엔드 시스템 설계 및 구조입니다.

## 주요 문서

### 핵심 아키텍처
- **[ARCHITECTURE.md](./ARCHITECTURE.md)** - 전체 시스템 아키텍처 (통합 문서)
- **[백테스트_데이터_통합_전략.md](./백테스트_데이터_통합_전략.md)** - 백테스트 데이터 통합 핵심 전략

### 인증
- **[auth-architecture.md](./auth-architecture.md)** - 인증 아키텍처 (JWT + OAuth2 Google/Naver)
- **[email-phone-verification.md](./email-phone-verification.md)** - 이메일/전화번호 인증 계획

### 이벤트 & 트레이딩
- **[메시징 아키텍처 (Kafka→Pub/Sub 마이그레이션)](../../../docs/infra/kafka-to-pubsub-migration.md)** - GCP Pub/Sub 마이그레이션 전략
- **[trading-system-enhancements.md](./trading-system-enhancements.md)** - 실전 트레이딩 시스템 보완

### 데이터 아키텍처
- **[하이브리드_데이터베이스_전략.md](./하이브리드_데이터베이스_전략.md)** - PostgreSQL + MongoDB 하이브리드 전략

### 스케줄러 및 외부 연동
- **[스케줄러_아키텍처.md](./스케줄러_아키텍처.md)** - Quartz 스케줄러 구조 및 설계
  - 각 분석 Job의 상세 동작 → [분석 기능 문서](../features/ANALYSIS_ARCHITECTURE.md)
  - 운영 가이드 → [스케줄러 운영 가이드](../setup/스케줄러_운영_가이드.md)
- **[KIS_토큰_관리.md](./KIS_토큰_관리.md)** - 한국투자증권 API 토큰 관리
