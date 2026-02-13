# Backend 문서

알고리즘 트레이딩 플랫폼 Quant Jump Stock Backend 문서입니다.

## 폴더 구조

```
docs/
├── architecture/       # 시스템 아키텍처
│   ├── (기존 아키텍처 문서)
│   └── refactor/       # 리팩토링 계획/리뷰
├── plans/              # 백엔드 구현 계획
├── database/           # 데이터베이스 스키마
├── features/           # 주요 기능 & 분석
├── guidelines/         # 개발 가이드라인
├── setup/              # 개발 환경 & 배포
├── analysis/           # 데이터 분석 결과
└── kis/                # KIS API 연동
```

## 빠른 시작

| 상황 | 문서 |
|------|------|
| 처음 시작 | [환경설정 가이드](./setup/환경설정_가이드.md) |
| 시스템 구조 이해 | [아키텍처](./architecture/ARCHITECTURE.md) |
| 배포 방법 | [배포 가이드](./setup/배포_운영_가이드.md) |
| 스케줄러 운영 | [스케줄러 가이드](./setup/스케줄러_운영_가이드.md) |
| 기술 개선사항 | [Backend TODO](../TODO.md) |

## 주요 섹션

### [아키텍처](./architecture/)
- 시스템 아키텍처, 이벤트 기반 구조, DB 전략
- Kafka 이벤트 스키마, 스케줄러 구조
- 인증 아키텍처 (JWT + OAuth2)
- [리팩토링 계획](./architecture/refactor/) — 헥사고날 아키텍처 완성도 개선

### [구현 계획](./plans/)
- 전략 관리 시스템
- 백테스트 데이터 통합
- 트레이딩 시스템 개선
- 멀티 벤치마크
- 전략-유니버스 디커플링

### [설정 및 배포](./setup/)
- 환경 설정, 인증, 환경변수 관리
- 배포 가이드, 스케줄러 운영, Slack 연동

### [데이터베이스](./database/)
- PostgreSQL + MongoDB 스키마
- 테이블/컬렉션 관계도

### [KIS API](./kis/)
- 한국투자증권 API 연동 가이드
- 사용자별 KIS 계정 관리

## 현재 상태

### 완료됨
- Phase 1: 기본 인프라 구축
- Phase 2: Event-Driven Architecture
- PostgreSQL + MongoDB 하이브리드 DB
- 9개 Quartz Scheduler Job
- Vertex AI 예측 모델 통합

### 진행 중
- Phase 3: 자동 매매 시스템 (85%)
- 헥사고날 아키텍처 리팩토링

---

**마지막 업데이트**: 2026-02-13
